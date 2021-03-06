package com.timepath.major;

import com.google.protobuf.ByteString;
import com.timepath.major.proto.Messages;
import com.timepath.vfs.SimpleVFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class DefaultServer extends AbstractServer {

    private static final Logger LOG = Logger.getLogger(DefaultServer.class.getName());

    private final SimpleVFile vfs;
    ExecutorService pool;

    public DefaultServer(SimpleVFile vfs) {
        super(0);
        this.vfs = vfs;
        LOG.log(Level.INFO, "Starting server on port {0}", port);
        pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
    }

    @Override
    void connected(@NotNull final SocketChannel client) {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    @NotNull ProtoConnection c = new ProtoConnection(client.socket()) {
                        Messages.File wrap(@NotNull SimpleVFile file) {
                            return Messages.File.newBuilder()
                                    .setName(file.getName())
                                    .setType(file.isDirectory() ? Messages.File.FileType.DIRECTORY : Messages.File.FileType.FILE)
                                    .setLastModified(file.lastModified())
                                    .setSize(file.length())
                                    .build();
                        }

                        @Callback
                        void list(@NotNull Messages.ListRequest lr, @NotNull Messages.Meta.Builder response) {
                            LOG.log(Level.INFO, "List {0}", lr);
                            Messages.ListResponse.Builder list = Messages.ListResponse.newBuilder();
                            @Nullable SimpleVFile found = vfs.query(lr.getPath());
                            if (found != null) {
                                for (@NotNull SimpleVFile file : found.list()) {
                                    list.addFile(wrap(file));
                                }
                            }
                            response.setFiles(list.build());
                        }

                        @Callback
                        void info(@NotNull Messages.InfoRequest ir, @NotNull Messages.Meta.Builder response) {
                            LOG.log(Level.INFO, "Info {0}", ir);
                            Messages.InfoResponse.Builder info = Messages.InfoResponse.newBuilder();
                            @Nullable SimpleVFile found = vfs.query(ir.getPath());
                            if (found != null) {
                                info.setFile(wrap(found));
                            }
                            response.setFileInfo(info.build());
                        }

                        @Callback
                        void request(@NotNull Messages.ChunkRequest cr, @NotNull Messages.Meta.Builder response) {
                            LOG.log(Level.INFO, "Chunk {0}", cr);
                            Messages.FileChunk.Builder chunk = Messages.FileChunk.newBuilder();
                            @Nullable SimpleVFile found = vfs.query(cr.getPath());
                            data:
                            if (found != null) {
                                try (@Nullable InputStream is = found.openStream()) {
                                    if (is == null) break data;
                                    is.skip(cr.getOffset());
                                    @NotNull byte[] b = new byte[(int) Math.min(cr.getLength(), found.length())];
                                    int total = is.read(b, 0, b.length);
                                    if (total >= 0) chunk.setData(ByteString.copyFrom(b, 0, total));
                                } catch (IOException e) {
                                    LOG.log(Level.SEVERE, null, e);
                                }
                            }
                            response.setChunk(chunk);
                        }
                    };
                    c.readLoop();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, null, e);
                }
            }
        });
    }
}
