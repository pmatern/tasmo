package com.jivesoftware.os.tasmo.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivesoftware.os.tasmo.id.Id;
import com.jivesoftware.os.tasmo.id.ObjectId;
import com.jivesoftware.os.tasmo.id.TenantId;
import com.jivesoftware.os.tasmo.id.TenantIdAndCentricId;
import com.jivesoftware.os.tasmo.lib.write.ViewFieldChange;
import com.jivesoftware.os.tasmo.view.reader.service.writer.ViewWriteFieldChange;
import java.io.IOException;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

public class WriteChangeSerializationTest {

    @Test
    public void testJsonRoundTrip() throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        TenantId tenant = new TenantId("booya");
        ViewFieldChange.ViewFieldChangeType type = ViewFieldChange.ViewFieldChangeType.add;
        ObjectId viewId = new ObjectId("radical", new Id(234));
        String viewFieldName = "stuff";
        ObjectId[] modelPathIds = new ObjectId[]{new ObjectId("radParent", new Id(768))};

        ObjectNode value = mapper.createObjectNode();
        value.put("wow", "this should work");
        value.put("hrm", 5345);

        long now = System.currentTimeMillis();

        String valAsString = mapper.writeValueAsString(value);

        ViewFieldChange viewFieldChange = new ViewFieldChange(
            1, -1, -1,
            new TenantIdAndCentricId(tenant, Id.NULL), new Id(1234), type, viewId, viewFieldName, modelPathIds, valAsString, now);

        System.out.println("Serializing:\n" + mapper.writeValueAsString(viewFieldChange));

        byte[] serialized = mapper.writeValueAsBytes(viewFieldChange);
        ViewWriteFieldChange change = mapper.readValue(serialized, 0, serialized.length, ViewWriteFieldChange.class);

        System.out.println("De-serializing:\n" + mapper.writeValueAsString(change));

        Assert.assertNotNull(change);

        Assert.assertEquals(change.getTenantIdAndCentricId(), viewFieldChange.getTenantIdAndCentricId());
        Assert.assertEquals(change.getType().name(), viewFieldChange.getType().name());
        boolean what = change.getViewObjectId().equals(viewFieldChange.getViewObjectId());
        Assert.assertEquals(change.getViewObjectId(), viewFieldChange.getViewObjectId());

        Assert.assertTrue(Arrays.equals(change.getModelPathInstanceIds(), viewFieldChange.getModelPathInstanceIds()));

        Assert.assertEquals(change.getValue(), viewFieldChange.getValue());
        Assert.assertEquals(change.getTimestamp(), viewFieldChange.getTimestamp());

    }
}
