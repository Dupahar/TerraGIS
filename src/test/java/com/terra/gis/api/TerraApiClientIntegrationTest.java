package com.terra.gis.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.terra.gis.proto.PingRequest;
import com.terra.gis.proto.PingResponse;
import com.terra.gis.proto.TerraApiServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

class TerraApiClientIntegrationTest {

    @Test
    void ping_returnsExpectedResponseFromMockServer() throws Exception {
        Server server = NettyServerBuilder.forPort(6565)
                .addService(new TerraApiServiceGrpc.TerraApiServiceImplBase() {
                    @Override
                    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
                        PingResponse response = PingResponse.newBuilder()
                                .setMessage("pong:" + request.getClientId())
                                .setServerTimeEpochMs(123L)
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();

        try (TerraApiClient client = new TerraApiClient("localhost", 6565)) {
            TerraApiClient.PingResult result = client.ping("test-client");
            assertEquals("pong:test-client", result.message());
            assertEquals(123L, result.serverTimeEpochMs());
        } finally {
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    @Test
    void ping_throwsForBlankClientId() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext().build();
        try (TerraApiClient client = new TerraApiClient(channel)) {
            assertThrows(IllegalArgumentException.class, () -> client.ping(" "));
        }
    }
}
