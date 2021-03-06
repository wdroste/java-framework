/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.framework.async.impl;

import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.SchemaAsyncApiOp;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.objects.Schema;

import com.google.protobuf.ByteString;

public class SchemaAsyncApiOpImpl extends AbstractAPIOperation implements SchemaAsyncApiOp {

    private static final Log logger = Log.getLog(SchemaAsyncApiOpImpl.class);

    public SchemaAsyncApiOpImpl(
            RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            ConnectorKey connectorKey,
            Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction,timeout);
    }

    public Schema schema() {
        return asyncTimeout(schemaAsync());
    }

    public Promise<Schema, RuntimeException> schemaAsync() {
        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationMessages.OperationRequest.newBuilder().setSchemaOpRequest(
                        OperationMessages.SchemaOpRequest.getDefaultInstance())));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Schema, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationMessages.OperationRequest.Builder operationRequest) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<Schema, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {

            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder);
            } else {
                return null;
            }
        }

        protected OperationMessages.OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Schema, OperationMessages.SchemaOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Schema, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected OperationMessages.SchemaOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasSchemaOpResponse()) {
                return message.getSchemaOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "SchemaOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.SchemaOpResponse message) {
            if (!message.getSchema().isEmpty()) {
                getResultHandler().handleResult(
                        MessagesUtil.<Schema> deserializeLegacy(message.getSchema()));
            } else {
                getResultHandler().handleResult(null);
            }
        }
    }

    // -------

    public static AbstractLocalOperationProcessor<ByteString, OperationMessages.SchemaOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.SchemaOpRequest message) {
        return new InternalLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalLocalOperationProcessor extends
            AbstractLocalOperationProcessor<ByteString, OperationMessages.SchemaOpRequest> {

        protected InternalLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                OperationMessages.SchemaOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, ByteString result) {

            OperationMessages.SchemaOpResponse.Builder response =
                    OperationMessages.SchemaOpResponse.newBuilder();
            if (null != result) {
                response.setSchema(result);
            }
            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setSchemaOpResponse(response));
        }

        protected ByteString executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.SchemaOpRequest requestMessage) {
            Schema schema = connectorFacade.schema();
            if (null != schema) {
                return MessagesUtil.serializeLegacy(schema);
            }
            return null;

        }
    }
}
