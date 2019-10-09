package reactor;

import com.arangodb.entity.CollectionType;
import com.arangodb.internal.ArangoDefaults;
import com.arangodb.internal.velocypack.VPackDeserializers;
import com.arangodb.internal.velocypack.VPackSerializers;
import com.arangodb.internal.velocystream.internal.AuthenticationRequest;
import com.arangodb.internal.velocystream.internal.Chunk;
import com.arangodb.internal.velocystream.internal.Message;
import com.arangodb.velocypack.VPack;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocystream.Request;
import com.arangodb.velocystream.RequestType;
import com.arangodb.velocystream.Response;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Michele Rastelli
 */
public class TcpClientApplication {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TcpClientApplication.class);
    private static final byte[] PROTOCOL_HEADER = "VST/1.0\r\n\r\n".getBytes();

    static class ArangoTcpConnection {
        private NettyOutbound out;
        private Connection connection;
        private ConnectionProvider connectionProvider = ConnectionProvider.fixed("tcp");

        public void send(ByteArrayOutputStream authStream) {
            out.sendByteArray(Mono.just(PROTOCOL_HEADER))
                    .then(out.sendByteArray(Mono.just(authStream.toByteArray())).then())
                    .then()
                    .subscribe();
        }

        public void connect() {
            TcpClient.create(connectionProvider)
                    .wiretap(true)
                    .host("127.0.0.1")
                    .port(8529)
                    .handle(this::handleConnection)
                    .doOnConnected(c -> {
                        connection = c;
                    })
                    .connect()
                    .block();
        }

        private Publisher<Void> handleConnection(NettyInbound in, NettyOutbound out) {
            this.out = out;

            in.receive()
                    .asByteArray()
                    .map(v -> {
                        int len = v.length;
                        final Chunk chunk = readChunk(v);

                        ByteBuffer buf = ByteBuffer.wrap(v, len - chunk.getContentLength(), chunk.getContentLength()).order(ByteOrder.LITTLE_ENDIAN);
                        byte[] arr = new byte[buf.remaining()];
                        buf.get(arr);

                        return new Message(chunk.getMessageId(), arr);
                    })
                    .doOnNext(v -> {
                        System.out.println("---");
                        System.out.println(v.getId());
                        System.out.println(v.getHead());
                        System.out.println(v.getBody());
                        System.out.println("---");
                    })
                    .subscribe();

            return out.neverComplete();
        }

        public void disconnect() {
            this.connection.dispose();
        }

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ArangoTcpConnection conn = new ArangoTcpConnection();
        conn.connect();
        Thread.sleep(1000);
        conn.send(createAuthMessage());
        Thread.sleep(1000);
//        conn.send(createVersionRequest());
//        Thread.sleep(10000);
        conn.disconnect();
    }

    private static AtomicInteger messageCounter = new AtomicInteger();
    private static VPack vpacker = new VPack.Builder().serializeNullValues(false)

            .registerSerializer(Request.class, VPackSerializers.REQUEST)
            .registerSerializer(AuthenticationRequest.class, VPackSerializers.AUTH_REQUEST)

            .registerDeserializer(Response.class, VPackDeserializers.RESPONSE)
            .registerDeserializer(CollectionType.class, VPackDeserializers.COLLECTION_TYPE)

            .build();

    private static ByteArrayOutputStream createAuthMessage() throws IOException {
        VPackSlice auth = vpacker.serialize(new AuthenticationRequest("root", "test", "plain"),
                new VPack.SerializeOptions().type(AuthenticationRequest.class));
        final Chunk authChunk = new Chunk(messageCounter.getAndIncrement(), 0, 1, -1, 0, auth.toString().length());

        ByteArrayOutputStream authStream = new ByteArrayOutputStream();
        authStream.write(getChunkHead(authChunk));
        authStream.write(auth.getBuffer());
        return authStream;
    }

    private static ByteArrayOutputStream createVersionRequest() throws IOException {
        final Request request = new Request("_system", RequestType.GET, "/_api/version");
        VPackSlice versionReq = vpacker.serialize(request);
        final Chunk authChunk = new Chunk(messageCounter.getAndIncrement(), 0, 1, -1, 0, versionReq.toString().length());

        ByteArrayOutputStream authStream = new ByteArrayOutputStream();
        authStream.write(getChunkHead(authChunk));
        authStream.write(versionReq.getBuffer());
        return authStream;
    }

    private static byte[] getChunkHead(final Chunk chunk) {
        final long messageLength = chunk.getMessageLength();
        final int headLength = messageLength > -1L ? 24 : 16;
        final int length = chunk.getContentLength() + headLength;
        final ByteBuffer buffer = ByteBuffer.allocate(headLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(chunk.getChunkX());
        buffer.putLong(chunk.getMessageId());
        if (messageLength > -1L) {
            buffer.putLong(messageLength);
        }
        return buffer.array();
    }

    private static Chunk readChunk(byte[] buffer) {
        final ByteBuffer chunkHeadBuffer = ByteBuffer.wrap(buffer, 0, ArangoDefaults.CHUNK_MIN_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        final int length = chunkHeadBuffer.getInt();
        final int chunkX = chunkHeadBuffer.getInt();
        final long messageId = chunkHeadBuffer.getLong();
        final long messageLength;
        final int contentLength;
        if ((1 == (chunkX & 0x1)) && ((chunkX >> 1) > 1)) {
            messageLength = ByteBuffer.wrap(buffer, ArangoDefaults.CHUNK_MIN_HEADER_SIZE, ArangoDefaults.LONG_BYTES).order(ByteOrder.LITTLE_ENDIAN).getLong();
            contentLength = length - ArangoDefaults.CHUNK_MAX_HEADER_SIZE;
        } else {
            messageLength = -1L;
            contentLength = length - ArangoDefaults.CHUNK_MIN_HEADER_SIZE;
        }
        final Chunk chunk = new Chunk(messageId, chunkX, messageLength, 0, contentLength);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Received chunk %s:%s from message %s", chunk.getChunk(), chunk.isFirstChunk() ? 1 : 0, chunk.getMessageId()));
        }

        return chunk;
    }
}
