/*
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-$year$ Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package com.jivesoftware.os.tasmo.lib;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivesoftware.os.tasmo.event.api.write.EventBuilder;
import com.jivesoftware.os.tasmo.id.ObjectId;
import org.testng.annotations.Test;

/**
 *
 */
public class MultiFieldMultiViewTest extends BaseTasmoTest {

    String ContentView = "ContentView";
    String ContainerView = "ContainerView";
    String originalAuthorName = "originalAuthorName";
    String originalAuthor = "originalAuthor";
    String lastName = "lastName";
    String firstName = "firstName";
    String userName = "userName";

    @Test
    public void testMultiFieldMultiView() throws Exception {
        String contentView1 = ContentView + "1";
        String contentView2 = ContentView + "2";
        Expectations expectations =
            initModelPaths(contentView1 + "::" + originalAuthor + "::Content.ref_originalAuthor.ref.User|User.firstName,lastName,userName", contentView2 + "::"
            + originalAuthor + "::Content.ref_originalAuthor.ref.User|User.firstName,lastName,userName");
        ObjectId authorId =
            write(EventBuilder.create(idProvider, "User", tenantId, actorId).set("firstName", "tom").set("lastName", "sawyer")
            .set("userName", "tsawyer").build());
        ObjectId contentId = write(EventBuilder.create(idProvider, "Content", tenantId, actorId).set("ref_originalAuthor", authorId).build());
        expectations.addExpectation(contentId, contentView1, originalAuthor, new ObjectId[]{contentId, authorId}, firstName, "tom");
        expectations.addExpectation(contentId, contentView1, originalAuthor, new ObjectId[]{contentId, authorId}, lastName, "sawyer");
        expectations.addExpectation(contentId, contentView1, originalAuthor, new ObjectId[]{contentId, authorId}, userName, "tsawyer");
        expectations.addExpectation(contentId, contentView2, originalAuthor, new ObjectId[]{contentId, authorId}, firstName, "tom");
        expectations.addExpectation(contentId, contentView2, originalAuthor, new ObjectId[]{contentId, authorId}, lastName, "sawyer");
        expectations.addExpectation(contentId, contentView2, originalAuthor, new ObjectId[]{contentId, authorId}, userName, "tsawyer");
        expectations.assertExpectation(tenantIdAndCentricId);
        expectations.clear();
        ObjectNode view1 = readView(tenantIdAndCentricId, actorId, new ObjectId(contentView1, contentId.getId()));
        String deserializationInput1 = mapper.writeValueAsString(view1);
        System.out.println("Input1:" + deserializationInput1);
        ObjectNode view2 = readView(tenantIdAndCentricId, actorId, new ObjectId(contentView2, contentId.getId()));
        String deserializationInput2 = mapper.writeValueAsString(view2);
        System.out.println("Input2:" + deserializationInput2);
    }
}
