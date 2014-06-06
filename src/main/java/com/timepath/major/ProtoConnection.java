package com.timepath.major;

import com.google.protobuf.GeneratedMessage;
import com.timepath.major.proto.Files.FileListing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author TimePath
 */
public abstract class ProtoConnection {

    protected final OutputStream os;
    protected final InputStream  is;

    protected ProtoConnection(Socket s) throws IOException {
        this.os = s.getOutputStream();
        this.is = s.getInputStream();
    }

    protected GeneratedMessage read() throws IOException {
        int length = ( is.read() << 8 ) | is.read();
        byte[] buf = new byte[length];
        int total = 0;
        while(total < length) {
            total += is.read(buf, total, length - total);
        }
        return FileListing.parseFrom(new ByteArrayInputStream(buf, 0, total));
    }

    protected void write(GeneratedMessage m) throws IOException {
        int length = m.getSerializedSize();
        os.write(( length & 0xFF00 ) >> 8);
        os.write(length & 0xFF);
        m.writeTo(os);
    }

    abstract void callback(Object o);
}
