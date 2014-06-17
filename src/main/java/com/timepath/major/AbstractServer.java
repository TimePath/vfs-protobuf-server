package com.timepath.major;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple NIO socket selecting server. Only role is to accept connections
 * <p/>
 * Invoke {@link #run()} to start listening
 *
 * @author TimePath
 */
public abstract class AbstractServer {

    private static final Logger LOG = Logger.getLogger(AbstractServer.class.getName());
    protected int                 port;
    private   ServerSocketChannel channel;
    private   Selector            acceptSelector;

    public AbstractServer(int port) {
        this.port = port;
    }

    /**
     * @return The port this instance is listening on. Never 0
     */
    public int getPort() {
        return port;
    }

    /**
     * Starts listening for connections. Will call {@link #bind()} if not already bound.
     *
     * @throws IOException
     */
    public void run() throws IOException {
        if(channel == null) bind();
        channel.register(acceptSelector, SelectionKey.OP_ACCEPT);
        //noinspection InfiniteLoopStatement
        while(true) {
            // Wait for events
            acceptSelector.select();
            Iterator<SelectionKey> keys = acceptSelector.selectedKeys().iterator();
            while(keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if(!key.isValid()) {
                    continue;
                }
                try {
                    // One phase per iteration, defer others
                    if(key.isAcceptable()) {
                        accept(key);
                    }
                } catch(Throwable t) { // This is fatal
                    LOG.log(Level.SEVERE, null, t);
                }
            }
        }
    }

    /**
     * Attempt to bind to the requested port
     *
     * @throws IOException
     *         If binding fails
     */
    public void bind() throws IOException {
        channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        acceptSelector = Selector.open();
        channel.bind(new InetSocketAddress(port));
        port = channel.socket().getLocalPort();
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        client.setOption(StandardSocketOptions.TCP_NODELAY, true);
        LOG.log(Level.INFO, "Client {0} connected", client);
        connected(client);
    }

    /**
     * Called in response to a connection accepted
     *
     * @param client
     *         The connection
     *
     * @throws IOException
     */
    abstract void connected(SocketChannel client) throws IOException;
}
