package com.timepath.major;

import com.google.protobuf.MessageLite;
import com.timepath.major.proto.Messages.FileListing;
import com.timepath.major.proto.Messages.FileListing.File;
import com.timepath.major.proto.Messages.FileListing.File.FileType;
import com.timepath.major.proto.Messages.Meta;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class ProtocolTest {

    private static final Logger LOG = Logger.getLogger(ProtocolTest.class.getName());

    public static void main(String[] args) throws IOException {
        final AbstractServer server = new AbstractServer(0) {
            @Override
            void connected(final SocketChannel client) throws IOException {
                ProtoConnection c = new ProtoConnection(client.socket()) {
                    @Callback
                    void listing(MessageLite l) throws IOException {
                        LOG.log(Level.INFO, "Got {0}", l);
                        write(l);
                    }
                };
                c.read();
                client.close();
            }
        };
        server.bind();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.run();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        ProtoConnection c = new ProtoConnection(new Socket("127.0.0.1", server.getPort())) {
            @Callback
            void listing(FileListing l) throws IOException {
                LOG.log(Level.INFO, "Got {0}", l);
            }
        };
        Meta m = Meta.newBuilder()
                     .setFiles(FileListing.newBuilder()
                                          .addFile(File.newBuilder()
                                                       .setName("text.txt")
                                                       .setBody("text")
                                                       .setType(FileType.FILE)
                                                       .build())
                                          .build())
                     .build();
        c.write(m);
    }
}
