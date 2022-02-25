import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class MultiplexServer {

    private static final int CLIENT_CODE_LENGTH = 1;
    private static final int MAX_FILENAME_LENGTH = 1024;

    public static void main(String[] args) throws IOException{

        //open a selector
        Selector monitor = Selector.open();

        ServerSocketChannel welcomeChannel = ServerSocketChannel.open();
        welcomeChannel.socket().bind(new InetSocketAddress(2000));

        //configure the serverSocketChannel to be non-blocking
        //(selector only works with non-blocking channels.)
        welcomeChannel.configureBlocking(false);

        //register the channel and event to be monitored
        //this causes a "selection key" object to be created for this channel
        welcomeChannel.register(monitor, SelectionKey.OP_ACCEPT);

        while (true) {
            // select() is a blocking call (so there is NO busy waiting here)
            // It returns only after at least one channel is selected,
            // or the current thread is interrupted
            int readyChannels = monitor.select();

            //select() returns the number of keys, possibly zero
            if (readyChannels == 0) {
                continue;
            }

            // elements in this set are the keys that are ready
            // i.e., a registered event has happened in each of those keys
            Set<SelectionKey> readyKeys = monitor.selectedKeys();

            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {

                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    // OS received a new connection request from some new client
                    ServerSocketChannel wChannel = (ServerSocketChannel) key.channel();
                    SocketChannel serveChannel = wChannel.accept();

                    //create the dedicated socket channel to serve the new client
                    serveChannel.configureBlocking(false);

                    //register the dedicated socket channel for reading
                    serveChannel.register(monitor, SelectionKey.OP_READ);
                }

                else if (key.isReadable()) {
                    //OS received one or more packets from one or more clients
                    SocketChannel serveChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(CLIENT_CODE_LENGTH);
                    int bytesToRead = CLIENT_CODE_LENGTH;

                    //make sure we read the entire server reply
                    while((bytesToRead -= serveChannel.read(buffer)) > 0);

                    byte[] a = new byte[CLIENT_CODE_LENGTH];
                    buffer.flip();
                    buffer.get(a);
                    String request = new String(a);
                    System.out.println("Request from a client: " + request);

                    switch(request){
                        case "L":
                            //send reply code to indicate request was accepted
                            sendReplyCode(serveChannel, "S");

                            File[] filesList = new File(".").listFiles();
                            StringBuilder allFiles = new StringBuilder();
                            if (filesList != null){
                                for (File f : filesList){
                                    //ignore directories
                                    if (!f.isDirectory()) {
                                        allFiles.append(f.getName());
                                        allFiles.append(" : ");
                                        allFiles.append(f.length());
                                        allFiles.append("\n");
                                    }
                                }
                            }

                            ByteBuffer data = ByteBuffer.wrap(allFiles.toString().getBytes());
                            serveChannel.write(data);

                            serveChannel.close();
                            break;

                        case "D":
                            //Read filename. Buffer "consumes" code byte after reading, so reading now begins at the filename.
                            buffer = ByteBuffer.allocate(MAX_FILENAME_LENGTH);
                            while((serveChannel.read(buffer)) >= 0);

                            //Move position pointer to beginning of buffer and limit pointer to previous position pointer
                            buffer.flip();

                            //buffer.remaining tells number of bytes in buffer
                            a = new byte[buffer.remaining()];

                            //Read from position pointer and stop at limit pointer
                            buffer.get(a);
                            String fileName = new String(a);

                            File f = new File(fileName);

                            if(f.delete()){
                                sendReplyCode(serveChannel, "S");
                            } else {
                                sendReplyCode(serveChannel, "F");
                            }

                            serveChannel.close();
                            break;

                        case "G":
                            //Send file to client:
                            //Receive code "G"
                            buffer = ByteBuffer.allocate(MAX_FILENAME_LENGTH);
                            while((serveChannel.read(buffer)) >= 0);

                            //Move position pointer to beginning of buffer and limit pointer to previous position pointer
                            buffer.flip();

                            //buffer.remaining tells number of bytes in buffer
                            a = new byte[buffer.remaining()];

                            //Read from position pointer and stop at limit pointer
                            buffer.get(a);
                            String fileNameG = new String(a);

                            File fileG = new File(fileNameG);

                            BufferedReader bufferedReader = new BufferedReader(new FileReader(fileNameG));

                            //Client sends filename, check if it exists
                                //If not, send "F"
                                //If it does, send "S"
                            if(fileG.exists()){
                                sendReplyCode(serveChannel, "S");
                                String textLine;
                                while ((textLine = bufferedReader.readLine())!=null){
                                    buffer = ByteBuffer.wrap(textLine.getBytes());
                                    serveChannel.write(buffer);
                                }
                            } else {
                                sendReplyCode(serveChannel, "F");
                            }

                            //Send file, user should be able to open it

                            serveChannel.close();
                            break;

                        case "R":
                            //Receive R and "old filename","new filename"
                            //Read filename. Buffer "consumes" code byte after reading, so reading now begins at the filename.
                            buffer = ByteBuffer.allocate(MAX_FILENAME_LENGTH);
                            while((serveChannel.read(buffer)) >= 0);

                            //Move position pointer to beginning of buffer and limit pointer to previous position pointer
                            buffer.flip();

                            //buffer.remaining tells number of bytes in buffer
                            a = new byte[buffer.remaining()];

                            //Read from position pointer and stop at limit pointer
                            buffer.get(a);

                            //Convert bytes to filename string and split between old and new filename
                            String oldNameNewName = new String(a);
                            String[] renameArray = oldNameNewName.split(",");

                            File oldFile = new File(renameArray[0]);
                            File newFile = new File(renameArray[1]);

                            //Send back "F" or "S" depending on success
                            if(oldFile.renameTo(newFile)){
                                sendReplyCode(serveChannel, "S");
                            } else {
                                sendReplyCode(serveChannel, "F");
                            }

                            break;

                        default:
                            System.out.println("Unknown command!");
                            //send reply code to indicate request was rejected.
                            sendReplyCode(serveChannel, "F");
                    }
                    //note that calling close() will automatically
                    // deregister the channel with the selector
                    serveChannel.close();
                }
            }
        }
    }

    private static void sendReplyCode (SocketChannel channel, String code) throws IOException{
        ByteBuffer data = ByteBuffer.wrap(code.getBytes());
        channel.write(data);
    }
}


