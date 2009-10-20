/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.test.engine;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.restlet.Application;
import org.restlet.Client;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Message;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.engine.http.HttpConstants;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.Resource;
import org.restlet.routing.Router;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This tests the ability of the connectors to handle chunked encoding.
 * 
 * The test uses each connector to PUT an entity that will be sent chunked and
 * also to receive a chunked response.
 */
public class ChunkedEncodingTestCase extends BaseConnectorsTestCase {

    public static class PutTestResource extends Resource {

        public PutTestResource(Context ctx, Request request, Response response) {
            super(ctx, request, response);
            getVariants().add(new Variant(MediaType.TEXT_XML));
        }

        @Override
        public boolean allowPut() {
            return true;
        }

        @Override
        public Representation represent(Variant variant) {
            return createTestXml();
        }

        @Override
        public void storeRepresentation(Representation entity) {
            checkForChunkedHeader(getRequest());

            final DomRepresentation dom = new DomRepresentation(entity);
            try {
                final Document doc = dom.getDocument();
                assertXML(dom);
                getResponse().setEntity(
                        new DomRepresentation(MediaType.TEXT_XML, doc));
            } catch (IOException ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    private static int LOOP_NUMBER = 50;

    static void assertXML(DomRepresentation entity) {
        try {
            final Document document = entity.getDocument();
            final Node root = document.getDocumentElement();
            final NodeList children = root.getChildNodes();

            assertEquals("root", root.getNodeName());
            assertEquals(2, children.getLength());
            assertEquals("child-0", children.item(0).getNodeName());
            assertEquals("name-0", children.item(0).getAttributes()
                    .getNamedItem("name").getNodeValue());
            assertEquals("child-1", children.item(1).getNodeName());
            assertEquals("name-1", children.item(1).getAttributes()
                    .getNamedItem("name").getNodeValue());

        } catch (IOException ex) {
            fail(ex.getMessage());
        } finally {
            entity.release();
        }
    }

    static void checkForChunkedHeader(Message message) {
        final Form parameters = (Form) message.getAttributes().get(
                "org.restlet.http.headers");
        final Parameter p = parameters
                .getFirst(HttpConstants.HEADER_TRANSFER_ENCODING);
        assertFalse(p == null);
        assertEquals("chunked", p.getValue());
    }

    private static Document createDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .newDocument();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Representation createTestXml() {
        final Document doc = createDocument();
        final Element root = doc.createElement("root");

        doc.appendChild(root);

        for (int i = 0; i < 2; i++) {
            final Element e = doc.createElement("child-" + i);
            e.setAttribute("name", "name-" + i);
            root.appendChild(e);
        }

        return new DomRepresentation(MediaType.TEXT_XML, doc);
    }

    boolean checkedForChunkedResponse;

    @Override
    protected void call(String uri) throws Exception {
        for (int i = 0; i < LOOP_NUMBER; i++) {
            sendPut(uri);
            sendGet(uri);
        }
    }

    @Override
    protected Application createApplication(Component component) {
        final Application application = new Application() {
            @Override
            public Restlet createInboundRoot() {
                final Router router = new Router(getContext());
                router.attach("/test", PutTestResource.class);
                return router;
            }
        };
        return application;
    }

    private void sendGet(String uri) throws Exception {
        final Request request = new Request(Method.GET, uri);
        final Response r = new Client(Protocol.HTTP).handle(request);
        try {
            assertEquals(r.getStatus().getDescription(), Status.SUCCESS_OK, r
                    .getStatus());
            assertXML(new DomRepresentation(r.getEntity()));
        } finally {
            r.release();
        }

    }

    private void sendPut(String uri) throws Exception {
        final Request request = new Request(Method.PUT, uri, createTestXml());
        final Response r = new Client(Protocol.HTTP).handle(request);

        try {
            if (this.checkedForChunkedResponse) {
                checkForChunkedHeader(r);
            }
            assertEquals(r.getStatus().getDescription(), Status.SUCCESS_OK, r
                    .getStatus());
            assertXML(new DomRepresentation(r.getEntity()));
        } finally {
            r.release();
        }

    }

    @Override
    public void setUp() {
        this.checkedForChunkedResponse = true;
    }

    @Override
    public void testJettyAndApache() throws Exception {
        // TODO to be fixed
        // super.testJettyAndApache();
    }

    @Override
    public void testJettyAndInternal() throws Exception {
        // Jetty will not send a chunked response when a client sends
        // Connection: close, which the default client helper does
        this.checkedForChunkedResponse = false;
        super.testJettyAndInternal();
    }

    @Override
    public void testNettyAndApache() throws Exception {
        this.checkedForChunkedResponse = false;
        super.testNettyAndApache();
    }

    @Override
    public void testNettyAndInternal() throws Exception {
        this.checkedForChunkedResponse = false;
        super.testNettyAndInternal();
    }

    @Override
    public void testNettyAndJdkNet() throws Exception {
        this.checkedForChunkedResponse = false;
        super.testNettyAndJdkNet();
    }

    @Override
    public void testSimpleAndInternal() throws Exception {
        // Simple will not send a chunked response when a client sends
        // Connection: close, which the default client helper does
        this.checkedForChunkedResponse = false;
        super.testSimpleAndInternal();
    }

    @Override
    public void testSimpleAndJdkNet() throws Exception {
        // TODO to be fixed
        // super.testSimpleAndJdkNet();
    }

}
