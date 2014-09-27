package com.timepath.major;

import com.timepath.major.proto.Messages.File;
import com.timepath.major.proto.Messages.File.FileType;
import com.timepath.major.proto.Messages.ListResponse;
import com.timepath.major.proto.Messages.Meta;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.fail;

/**
 * @author TimePath
 */
public class ProtocolTest {

    private static final Logger LOG = Logger.getLogger(ProtocolTest.class.getName());

    @Test
    public void communication() throws IOException, InterruptedException {
        @NotNull final AbstractServer server = new AbstractServer(0) {
            @Override
            void connected(@NotNull final SocketChannel clientChannel) throws IOException {
                @NotNull ProtoConnection client = new ProtoConnection(clientChannel.socket()) {
                    @Callback
                    void listing(ListResponse l, @NotNull Meta.Builder response) throws IOException {
                        LOG.log(Level.INFO, "Server got {0}", l);
                        response.setFiles(ListResponse.newBuilder()
                                .addFile(File.newBuilder()
                                        .setName("text.txt")
                                        .setType(FileType.FILE)
                                        .build())
                                .build());
                    }
                };
                client.receive(client.read());
            }
        };
        server.bind();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        @NotNull final Object done = new Object();
        @NotNull final ProtoConnection c = new ProtoConnection(new Socket("localhost", server.getPort())) {
            @Callback
            void listing(ListResponse l, Meta.Builder response) throws IOException {
                LOG.log(Level.INFO, "Client got {0}", l);
                synchronized (done) {
                    done.notify();
                }
            }
        };
        c.write(Meta.newBuilder()
                .setTag((int) System.currentTimeMillis())
                .setFiles(ListResponse.newBuilder()
                        .addFile(File.newBuilder().setName("text.txt").setType(FileType.FILE).build())
                        .build())
                .build());
        new Thread() {
            @Override
            public void run() {
                try {
                    for (Meta m; (m = c.read()) != null; ) {
                        c.receive(m);
                    }
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            }
        }.start();
        synchronized (done) {
            done.wait();
        }
    }
}
