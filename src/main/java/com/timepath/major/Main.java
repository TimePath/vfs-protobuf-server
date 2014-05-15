package com.timepath.major;

import com.timepath.major.proto.Addressbook.AddressBook;
import com.timepath.major.proto.Addressbook.Person;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
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
