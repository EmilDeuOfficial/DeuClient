package de.erik.discordglass.discord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class IpcPipe implements AutoCloseable {
   static final int OP_HANDSHAKE = 0;
   static final int OP_FRAME = 1;
   static final int OP_CLOSE = 2;
   static final int OP_PING = 3;
   static final int OP_PONG = 4;

   abstract void write(int var1, String var2) throws IOException;

   abstract IpcPipe.Frame poll() throws IOException;

   @Override
   public abstract void close();

   static byte[] encode(int op, String json) {
      byte[] data = json.getBytes(StandardCharsets.UTF_8);
      ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
      buf.putInt(op).putInt(data.length).put(data);
      return buf.array();
   }

   static IpcPipe open() throws IOException {
      IOException last = new IOException("Discord läuft nicht (keine IPC-Pipe gefunden)");
      boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

      for (int i = 0; i < 10; i++) {
         try {
            if (windows) {
               return new IpcPipe.WindowsPipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
            }

            for (Path dir : unixDirs()) {
               Path p = dir.resolve("discord-ipc-" + i);
               if (Files.exists(p)) {
                  SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                  ch.connect(UnixDomainSocketAddress.of(p));
                  ch.configureBlocking(false);
                  return new IpcPipe.UnixPipe(ch);
               }
            }
         } catch (IOException var9) {
            last = var9;
         }
      }

      throw last;
   }

   private static Path[] unixDirs() {
      String base = System.getenv("XDG_RUNTIME_DIR");
      if (base == null) {
         base = System.getenv("TMPDIR");
      }

      if (base == null) {
         base = System.getenv("TMP");
      }

      if (base == null) {
         base = "/tmp";
      }

      Path b = Path.of(base);
      return new Path[]{
         b, b.resolve("app/com.discordapp.Discord"), b.resolve("snap.discord"), b.resolve(".flatpak/dev.vencord.Vesktop/xdg-run"), Path.of("/tmp")
      };
   }

   record Frame(int op, String json) {
   }

   private static final class UnixPipe extends IpcPipe {
      private final SocketChannel ch;
      private final ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      private ByteBuffer body;
      private int bodyOp;

      UnixPipe(SocketChannel ch) {
         this.ch = ch;
      }

      @Override
      synchronized void write(int op, String json) throws IOException {
         ByteBuffer buf = ByteBuffer.wrap(encode(op, json));

         while (buf.hasRemaining()) {
            if (this.ch.write(buf) == 0) {
               Thread.onSpinWait();
            }
         }
      }

      @Override
      synchronized IpcPipe.Frame poll() throws IOException {
         if (this.body == null) {
            if (this.ch.read(this.header) < 0) {
               throw new IOException("Verbindung geschlossen");
            }

            if (this.header.hasRemaining()) {
               return null;
            }

            this.header.flip();
            this.bodyOp = this.header.getInt();
            this.body = ByteBuffer.allocate(this.header.getInt());
            this.header.clear();
         }

         if (this.ch.read(this.body) < 0) {
            throw new IOException("Verbindung geschlossen");
         } else if (this.body.hasRemaining()) {
            return null;
         } else {
            IpcPipe.Frame f = new IpcPipe.Frame(this.bodyOp, new String(this.body.array(), StandardCharsets.UTF_8));
            this.body = null;
            return f;
         }
      }

      @Override
      public void close() {
         try {
            this.ch.close();
         } catch (IOException var2) {
         }
      }
   }

   private static final class WindowsPipe extends IpcPipe {
      private final RandomAccessFile file;
      private final Object lock = new Object();

      WindowsPipe(RandomAccessFile file) {
         this.file = file;
      }

      @Override
      void write(int op, String json) throws IOException {
         synchronized (this.lock) {
            this.file.write(encode(op, json));
         }
      }

      @Override
      IpcPipe.Frame poll() throws IOException {
         synchronized (this.lock) {
            if (this.file.length() < 8L) {
               return null;
            } else {
               int op = Integer.reverseBytes(this.file.readInt());
               int len = Integer.reverseBytes(this.file.readInt());
               byte[] data = new byte[len];
               this.file.readFully(data);
               return new IpcPipe.Frame(op, new String(data, StandardCharsets.UTF_8));
            }
         }
      }

      @Override
      public void close() {
         try {
            this.file.close();
         } catch (IOException var2) {
         }
      }
   }
}
