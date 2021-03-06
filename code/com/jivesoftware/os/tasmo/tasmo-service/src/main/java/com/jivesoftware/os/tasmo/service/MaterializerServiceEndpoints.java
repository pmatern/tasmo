package com.jivesoftware.os.tasmo.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import com.jivesoftware.os.jive.utils.jaxrs.util.ResponseHelper;
import com.jivesoftware.os.jive.utils.logger.MetricLogger;
import com.jivesoftware.os.jive.utils.logger.MetricLoggerFactory;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@Singleton
@Path("/materializer")
public class MaterializerServiceEndpoints {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();
    @Context
    EventConvertingCallbackStream ingressWrittenEvents;

    @POST
    @Consumes("application/json")
    @Path("/writtenEvents")
    public Response writtenEvents(List<ObjectNode> events) {
        try {
            LOG.startTimer("writeEvents");
            LOG.inc("ingressed>total", events.size());
            for (ObjectNode event : events) {
                // TODO ensure doneYet tracking is disabled.
            }
            ingressWrittenEvents.callback(events);
            LOG.inc("ingressed>success", events.size());
            return ResponseHelper.INSTANCE.jsonResponse("success");
        } catch (Exception x) {
            LOG.inc("ingressed>errors");
            LOG.error("failed to ingress because:", x);
            return ResponseHelper.INSTANCE.errorResponse(null, x);
        } finally {
            LOG.stopTimer("writeEvents");
        }
    }
}
