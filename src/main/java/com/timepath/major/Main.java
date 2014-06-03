package com.timepath.major;

import com.timepath.major.vfs.DatabaseConnection;
import com.timepath.major.vfs.SecurityAdapter;
import com.timepath.major.vfs.SecurityController;
import com.timepath.vfs.SimpleVFile;
import com.timepath.vfs.ftp.FTPFS;
import com.timepath.vfs.jdbc.JDBCFS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        if(args.length == 0) args = new String[] { "jdbc:mysql://localhost/test" };
        if(args.length >= 2) {
            Class.forName(args[1]);
        }
        final JDBCFS jdbcfs = new DatabaseConnection(args[0]);
        FTPFS ftp = new FTPFS();
        ftp.add(new SecurityAdapter(jdbcfs, new SecurityController() {
            String user = System.getProperty("user.name");

            @Override
            public InputStream openStream(final SimpleVFile file) {
                if(Math.round(Math.random()) == 1) return new ByteArrayInputStream("Go away".getBytes());
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
        new AbstractServer(9001) {
            {
                LOG.log(Level.INFO, "Starting server on port {0}", port);
            }

            @Override
            void connected(SocketChannel client) throws IOException {
                InputStream is = client.socket().getInputStream();
                OutputStream os = client.socket().getOutputStream();
                try {
                    AddressBook addressBook = AddressBook.parseDelimitedFrom(is);
                    Person p = addressBook.getPerson(0);
                    LOG.log(Level.INFO, "Got {0}", p);
                    p.writeDelimitedTo(os);
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }.run();
    }
}
