package com.timepath.major;

import com.timepath.major.proto.Messages.*;
import com.timepath.major.proto.Messages.File.FileType;
import com.timepath.major.vfs.DatabaseConnection;
import com.timepath.major.vfs.SecurityAdapter;
import com.timepath.major.vfs.SecurityController;
import com.timepath.vfs.SimpleVFile;
import com.timepath.vfs.ftp.FTPFS;
import com.timepath.vfs.jdbc.JDBCFS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        if(args.length == 0) args = new String[] { "jdbc:postgresql://localhost/" + System.getProperty("user.name") };
        if(args.length >= 2) {
            try {
                Class.forName(args[1]);
            } catch(ClassNotFoundException e) {
                LOG.log(Level.SEVERE, null, e);
            }
        }
        final JDBCFS[] jdbcfs = new JDBCFS[1];
        try {
            jdbcfs[0] = new DatabaseConnection(args[0]);
            FTPFS ftp = new FTPFS();
            ftp.add(new SecurityAdapter(jdbcfs[0], new SecurityController() {
                String user = System.getProperty("user.name");

                @Override
                public InputStream openStream(final SimpleVFile file) {
                    if(Math.round(Math.random()) == 1) {
                        return new ByteArrayInputStream("Go away".getBytes(StandardCharsets.UTF_8));
                    }
                    return super.openStream(file);
                }

                @Override
                public Collection<? extends SimpleVFile> list(final SimpleVFile file) {
                    return file.list();
                }

                @Override
                public SimpleVFile get(final SimpleVFile file) {
                    LOG.log(Level.INFO, "{0} is accessing {1}", new Object[] { user, file });
                    return file;
                }
            }));
            new Thread(ftp).start();
        } catch(SQLException | IOException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        new AbstractServer(9001) {
            {
                LOG.log(Level.INFO, "Starting server on port {0}", port);
            }

            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

            @Override
            void connected(final SocketChannel client) {
                pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ProtoConnection c = new ProtoConnection(client.socket()) {
                                @Callback
                                void ignore(Integer seq, Meta.Builder response) {}

                                File wrap(SimpleVFile file) {
                                    return File.newBuilder()
                                               .setName(file.getName())
                                               .setType(file.isDirectory() ? FileType.DIRECTORY : FileType.FILE)
                                               .setLastModified(file.lastModified())
                                               .build();
                                }

                                @Callback
                                void list(ListRequest l, Meta.Builder response) {
                                    LOG.log(Level.INFO, "List {0}", l);
                                    ListResponse.Builder list = ListResponse.newBuilder();
                                    SimpleVFile found = jdbcfs[0].query(l.getPath());
                                    if(found != null) {
                                        for(SimpleVFile file : found.list()) {
                                            list.addFile(wrap(file));
                                        }
                                    }
                                    response.setFiles(list.build());
                                }

                                @Callback
                                void info(InfoRequest l, Meta.Builder response) {
                                    LOG.log(Level.INFO, "Info {0}", l);
                                    InfoResponse.Builder info = InfoResponse.newBuilder();
                                    SimpleVFile found = jdbcfs[0].query(l.getPath());
                                    if(found != null) {
                                        info.setFile(wrap(found));
                                    }
                                    response.setFileInfo(info.build());
                                }
                            };
                            for(Meta m; ( m = c.read() ) != null; ) {
                                c.callback(m);
                            }
                        } catch(Exception e) {
                            LOG.log(Level.SEVERE, null, e);
                        }
                    }
                });
            }
        }.run();
    }
}
