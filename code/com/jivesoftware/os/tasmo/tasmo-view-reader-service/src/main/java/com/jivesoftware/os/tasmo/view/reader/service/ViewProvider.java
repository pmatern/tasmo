/*
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-$year$ Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package com.jivesoftware.os.tasmo.view.reader.service;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jivesoftware.os.jive.utils.logger.MetricLogger;
import com.jivesoftware.os.jive.utils.logger.MetricLoggerFactory;
import com.jivesoftware.os.jive.utils.row.column.value.store.api.ColumnValueAndTimestamp;
import com.jivesoftware.os.tasmo.configuration.views.PathAndDictionary;
import com.jivesoftware.os.tasmo.configuration.views.TenantViewsProvider;
import com.jivesoftware.os.tasmo.id.Id;
import com.jivesoftware.os.tasmo.id.ImmutableByteArray;
import com.jivesoftware.os.tasmo.id.TenantId;
import com.jivesoftware.os.tasmo.id.TenantIdAndCentricId;
import com.jivesoftware.os.tasmo.model.path.ModelPath;
import com.jivesoftware.os.tasmo.view.reader.api.ViewDescriptor;
import com.jivesoftware.os.tasmo.view.reader.api.ViewFieldVersion;
import com.jivesoftware.os.tasmo.view.reader.api.ViewReader;
import com.jivesoftware.os.tasmo.view.reader.api.ViewResponse;
import com.jivesoftware.os.tasmo.view.reader.service.shared.ViewValueStore.ViewCollector;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class ViewProvider<V> implements ViewReader<V> {

    private static final String VIEW_READ_LATENCY = "view>read>latency";
    private static final String VIEW_PERMISSIONS_LATENCY = "view>permissions>latency";
    private static final String VIEW_MERGE_LATENCY = "view>merge>latency";
    private static final String VIEW_READ_VIEW_COUNT = "view>read>view>count";
    private static final String VIEW_READ_CALL_COUNT = "view>read>call>count";

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();
    private final ViewPermissionChecker viewPermissionChecker;
    private final ViewValueReader viewValueReader;
    private final TenantViewsProvider tenantViewsProvider;
    private final ViewFormatter<V> viewFormatter;
    private final JsonViewMerger merger;
    private final StaleViewFieldStream staleViewFieldStream;

    public ViewProvider(ViewPermissionChecker viewPermissionChecker,
            ViewValueReader viewValueReader,
            TenantViewsProvider viewModel,
            ViewFormatter<V> viewFormatter,
            JsonViewMerger merger,
            StaleViewFieldStream staleViewFieldStream) {
        this.viewPermissionChecker = viewPermissionChecker;
        this.viewValueReader = viewValueReader;
        this.tenantViewsProvider = viewModel;
        this.viewFormatter = viewFormatter;
        this.merger = merger;
        if (staleViewFieldStream == null) {
            throw new IllegalStateException("deadFieldStateStream cannot be null.");
        }

        this.staleViewFieldStream = staleViewFieldStream;

    }

    @Override
    public V readView(ViewDescriptor request) throws IOException {
        List<V> views = readViews(ImmutableList.of(request));
        return views.isEmpty() ? viewFormatter.emptyView() : views.get(0);
    }

    @Override
    public List<V> readViews(List<ViewDescriptor> request) throws IOException {
        LOG.inc(VIEW_READ_CALL_COUNT);
        Preconditions.checkArgument(request != null);
        Map<TenantAndActor, Set<Id>> permisionCheckTheseIds = new HashMap<>();
        List<ViewCollectorImpl<V>> viewCollectors = buildViewCollectors(request, permisionCheckTheseIds);

        LOG.startTimer(VIEW_READ_LATENCY);
        try {
            viewValueReader.readViewValues(viewCollectors);
        } finally {
            LOG.stopTimer(VIEW_READ_LATENCY);
        }

        Map<TenantAndActor, Set<Id>> canViewTheseIds;
        LOG.startTimer(VIEW_PERMISSIONS_LATENCY);
        try {
            canViewTheseIds = checkPermissions(permisionCheckTheseIds);
        } finally {
            LOG.stopTimer(VIEW_PERMISSIONS_LATENCY);
        }

        LOG.startTimer(VIEW_MERGE_LATENCY);
        try {
            List<V> views = collectViewObject(viewCollectors, canViewTheseIds);
            LOG.inc(VIEW_READ_VIEW_COUNT, views.size());
            return views;
        } catch (Exception ex) {
            LOG.error("Failed while loading {}", request);
            throw new IOException("Failed to load for the following reason.", ex);
        } finally {
            LOG.stopTimer(VIEW_MERGE_LATENCY);
        }
    }

    List<ViewCollectorImpl<V>> buildViewCollectors(List<ViewDescriptor> request, Map<TenantAndActor, Set<Id>> permisionCheckTheseIds) {
        List<ViewCollectorImpl<V>> viewCollectors = Lists.newArrayList();
        for (ViewDescriptor viewDescriptor : request) {
            TenantAndActor tenantAndActor = new TenantAndActor(viewDescriptor.getTenantId(), viewDescriptor.getActorId());
            Set<Id> accumulateIdsToBePermissionsChecked = permisionCheckTheseIds.get(tenantAndActor);
            if (accumulateIdsToBePermissionsChecked == null) {
                accumulateIdsToBePermissionsChecked = new HashSet<>();
                permisionCheckTheseIds.put(tenantAndActor, accumulateIdsToBePermissionsChecked);
            }

            Map<Integer, PathAndDictionary> viewFieldBindings = tenantViewsProvider.getViewFieldBinding(
                    viewDescriptor.getTenantIdAndCentricId().getTenantId(), viewDescriptor.getViewId().getClassName());

            // TODO: be able to pass back error result per descriptor to front end
            if (viewFieldBindings == null) {
                LOG.error(viewDescriptor.getViewId().getClassName() + " has no declared view bindings.");
            }

            ViewFieldsCollector viewFieldsCollector = new ViewFieldsCollector(merger);
            viewCollectors.add(new ViewCollectorImpl<>(viewDescriptor,
                    viewFieldBindings,
                    viewFieldsCollector,
                    staleViewFieldStream,
                    accumulateIdsToBePermissionsChecked,
                    viewFormatter));
        }
        return viewCollectors;
    }

    List<V> collectViewObject(List<ViewCollectorImpl<V>> viewCollectors, Map<TenantAndActor, Set<Id>> canViewTheseIds) throws Exception {
        List<V> result = new ArrayList<>();
        for (ViewCollectorImpl<V> viewCollector : viewCollectors) {
            ViewDescriptor viewDescriptor = viewCollector.getViewDescriptor();
            TenantAndActor tenantAndActor = new TenantAndActor(viewDescriptor.getTenantId(), viewDescriptor.getActorId());
            result.add(viewCollector.getView(canViewTheseIds.get(tenantAndActor)));
        }
        return result;
    }

    Map<TenantAndActor, Set<Id>> checkPermissions(Map<TenantAndActor, Set<Id>> permissionCheckTheseIds) {
        Map<TenantAndActor, Set<Id>> canViewTheseIds = new HashMap<>();
        for (TenantAndActor tenantAndActor : permissionCheckTheseIds.keySet()) { // 1 permissions check call per tenant and actor id tuple.
            Collection<Id> ids = permissionCheckTheseIds.get(tenantAndActor);
            ViewPermissionCheckResult checkResult = viewPermissionChecker.check(tenantAndActor.tenantId,
                    tenantAndActor.actorId, new HashSet<>(ids));
            canViewTheseIds.put(tenantAndActor, Sets.union(checkResult.allowed(), checkResult.unknown())); // For now... TODO
        }
        return canViewTheseIds;
    }

    static class ViewCollectorImpl<VV> implements ViewCollector {

        private static final MetricLogger LOG = MetricLoggerFactory.getLogger();
        private final ViewDescriptor viewDescriptor;
        private final Map<Integer, PathAndDictionary> viewClassFieldBindings;
        private final ViewFieldsCollector viewFieldsCollector;
        private final StaleViewFieldStream staleViewFieldStream;
        private final Set<Id> permissionCheckTheseIds;
        private final ViewFormatter<VV> viewFormatter;

        public ViewCollectorImpl(
                ViewDescriptor viewDescriptor,
                Map<Integer, PathAndDictionary> viewClassFieldBindings,
                ViewFieldsCollector viewFieldsCollector,
                StaleViewFieldStream staleViewFieldStream,
                Set<Id> permissionCheckTheseIds,
                ViewFormatter<VV> viewFormatter) {
            this.viewDescriptor = viewDescriptor;
            this.viewClassFieldBindings = viewClassFieldBindings;
            this.viewFieldsCollector = viewFieldsCollector;
            this.staleViewFieldStream = staleViewFieldStream;
            this.permissionCheckTheseIds = permissionCheckTheseIds;
            this.viewFormatter = viewFormatter;
        }

        public VV getView(Set<Id> canViewTheseIds) throws Exception {
            ViewResponse view = viewFieldsCollector.getView(canViewTheseIds);
            List<ViewFieldVersion> viewValueFieldVersions = viewFieldsCollector.getViewValueFieldVersions();
            TenantIdAndCentricId tenantIdAndCentricId = viewDescriptor.getTenantIdAndCentricId();
            return viewFormatter.getView(tenantIdAndCentricId, viewDescriptor.getViewId(), view, viewValueFieldVersions);
        }

        @Override
        public ViewDescriptor getViewDescriptor() {
            return viewDescriptor;
        }

        @Override
        public ColumnValueAndTimestamp<ImmutableByteArray, String, Long> callback(
                ColumnValueAndTimestamp<ImmutableByteArray, String, Long> fieldValue) throws Exception {
            if (viewClassFieldBindings == null) { // if factoed out so that we dont exceed 4 levels of if nesting.
                return fieldValue;
            }
            if (fieldValue != null) {
                ByteBuffer bb = ByteBuffer.wrap(fieldValue.getColumn().getImmutableBytes());
                Integer modelPathIdHashCode = bb.getInt();
                PathAndDictionary pathAndDictionary = viewClassFieldBindings.get(modelPathIdHashCode);

                if (pathAndDictionary != null) {
                    ModelPath modelPath = pathAndDictionary.getPath();
                    if (modelPath != null) {
                        int pathComboKey = bb.getInt();
                        String[] viewPathClasses = pathAndDictionary.getDictionary().lookupModelPathClasses(pathComboKey);

                        if (viewPathClasses != null && viewPathClasses.length > 0) {
                            Id[] modelPathIds = modelPathIds(bb, modelPath.getPathMemberSize());
                            if (viewFieldsCollector.add(modelPath, modelPathIds, viewPathClasses, fieldValue.getValue(), fieldValue.getTimestamp())) {
                                permissionCheckTheseIds.addAll(Arrays.asList(modelPathIds));
                            }
                        } else {
                            LOG.warn("Unable to look up model path classes for view path with path combination key: " + pathComboKey);
                        }
                    } else {
                        LOG.warn("failed to load ViewValueBinding for viewValueBindingKey={}, fieldValue={} ", new Object[]{modelPathIdHashCode,
                            fieldValue});
                    }
                } else {
                    LOG.debug("Failed to load model path and view path dictionary from column key. Older column key format is likely the case.");
                    try {
                        staleViewFieldStream.stream(viewDescriptor, fieldValue);
                    } catch (Exception x) {
                        LOG.error("Implementer of staleViewFieldStream is failing to handle all exception appropriately. ", x);
                    }
                }
            } else {
                viewFieldsCollector.done(); //eos
            }
            return fieldValue;
        }

        private Id[] modelPathIds(ByteBuffer bb, int count) {
            Id[] ids = new Id[count];
            for (int i = 0; i < count; i++) {
                int l = bb.get();
                byte[] idBytes = new byte[l];
                bb.get(idBytes);
                ids[i] = new Id(idBytes);
            }
            return ids;
        }

    }

    static class TenantAndActor {

        private final TenantId tenantId;
        private final Id actorId;

        TenantAndActor(TenantId tenantId, Id actorId) {
            this.tenantId = tenantId;
            this.actorId = actorId;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.tenantId);
            hash = 79 * hash + Objects.hashCode(this.actorId);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TenantAndActor other = (TenantAndActor) obj;
            if (!Objects.equals(this.tenantId, other.tenantId)) {
                return false;
            }
            if (!Objects.equals(this.actorId, other.actorId)) {
                return false;
            }
            return true;
        }
    }
}
