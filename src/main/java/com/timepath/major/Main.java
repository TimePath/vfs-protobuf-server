package com.timepath.major;

import com.timepath.major.proto.Files.FileListing;
import com.timepath.major.vfs.DatabaseConnection;
import com.timepath.major.vfs.SecurityAdapter;
import com.timepath.major.vfs.SecurityController;
import com.timepath.vfs.SimpleVFile;
import com.timepath.vfs.ftp.FTPFS;
import com.timepath.vfs.jdbc.JDBCFS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
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

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Callback {}

    public static void main(String[] args) throws IOException {
        if(args.length == 0) args = new String[] { "jdbc:mysql://localhost/test" };
        if(args.length >= 2) {
            try {
                Class.forName(args[1]);
            } catch(ClassNotFoundException e) {
                LOG.log(Level.SEVERE, null, e);
            }
        }
        try {
            final JDBCFS jdbcfs = new DatabaseConnection(args[0]);
            FTPFS ftp = new FTPFS();
            ftp.add(new SecurityAdapter(jdbcfs, new SecurityController() {
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
                                private boolean isApplicable(Method method, Object o) {
                                    if(method.getAnnotation(Callback.class) == null) return false;
                                    Class[] c = method.getParameterTypes();
                                    if(c.length > 1) return false;
                                    return c[0].isInstance(o);
                                }

                                @Override
                                void callback(Object o) {
                                    if(o == null) throw new RuntimeException("Null callback object.");
                                    Method m = null;
                                    for(Method method : getClass().getDeclaredMethods()) {
                                        if(isApplicable(method, o)) {
                                            m = method;
                                            break;
                                        }
                                    }
                                    if(m == null) {
                                        throw new RuntimeException("No callback for '" + o.getClass() + "'.");
                                    }
                                    try {
                                        m.invoke(this, o);
                                    } catch(Exception e) {
                                        throw new RuntimeException("Callback failed for '" + o.getClass() + "'.", e);
                                    }
                                }

                                @Callback
                                void listing(FileListing l) throws IOException {
                                    LOG.log(Level.INFO, "Got {0}", l);
                                    write(l);
                                }
                            };
                            while(true) {
                                c.callback(c.read());
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
