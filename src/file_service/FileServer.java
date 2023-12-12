package file_service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class FileServer {

  private static final ExecutorService executorService = Executors.newFixedThreadPool(
    3
  );
  private static final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
  private static final String BASE_PATH =
    System.getProperty("user.dir");
  private static volatile boolean isShutdownRequested = false;

  public static void main(String[] args) throws Exception {
    int port = 3000;
    ServerSocketChannel welcomeChannel = ServerSocketChannel.open();
    welcomeChannel.socket().bind(new InetSocketAddress(port));
    System.out.println("Listening on port " + port);

    // start new thread that waits for shutdown command
    new Thread(() -> {
      try (Scanner scanner = new Scanner(System.in)) {
        while (true) {
          String command = scanner.nextLine().toUpperCase();
          if ("Q".equals(command)) {
            isShutdownRequested = true;
            executorService.shutdown();
            try {
              if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (
                  !executorService.awaitTermination(60, TimeUnit.SECONDS)
                ) System.err.println("Executor service did not terminate");
              }
            } catch (InterruptedException ie) {
              executorService.shutdownNow();
              Thread.currentThread().interrupt();
            }
            System.exit(0);
          }
        }
      }
    })
      .start();

    // handle client requests in a loop
    while (!isShutdownRequested) {
      SocketChannel serveChannel = welcomeChannel.accept();
      executorService.submit(() -> {
        try {
          handleClientRequest(serveChannel);
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    }

    welcomeChannel.close();
  }

  private static void handleDelete(
    SocketChannel serveChannel,
    ByteBuffer request
  ) throws IOException {
    int fileNameLength = request.getInt();
    byte[] d = new byte[fileNameLength];
    request.get(d);
    String fileName = new String(d, StandardCharsets.UTF_8);

    String filePath = BASE_PATH + File.separator + fileName;

    ReentrantLock lock = fileLocks.computeIfAbsent(
      filePath,
      k -> new ReentrantLock()
    );
    lock.lock();
    try {
      Path path = Paths.get(filePath);
      if (Files.exists(path) && Files.isRegularFile(path)) {
        Files.delete(path);
        serveChannel.write(ByteBuffer.wrap("S".getBytes()));
      } else {
        serveChannel.write(ByteBuffer.wrap("F".getBytes()));
      }
    } finally {
      lock.unlock();
      serveChannel.close();
    }
  }

  private static void handleRename(
    SocketChannel serveChannel,
    ByteBuffer request
  ) throws IOException {
    int oldNameLength = request.getInt();
    byte[] oldNameBytes = new byte[oldNameLength];
    request.get(oldNameBytes);
    String oldName = new String(oldNameBytes, StandardCharsets.UTF_8);
    System.out.println(oldName);
    System.out.println(oldName);

    int newNameLength = request.getInt();
    System.out.println(oldName);
    byte[] newNameBytes = new byte[newNameLength];
    System.out.println(oldName);
    request.get(newNameBytes);
    System.out.println(oldName);
    String newName = new String(newNameBytes, StandardCharsets.UTF_8);


    String oldFilePath = BASE_PATH + File.separator + oldName;
    String newFilePath = BASE_PATH + File.separator + newName;

    ReentrantLock lock = fileLocks.computeIfAbsent(
      oldFilePath,
      k -> new ReentrantLock()
    );
    lock.lock();
    try {
      Path oldPath = Paths.get(oldFilePath);
      if (Files.exists(oldPath) && Files.isRegularFile(oldPath)) {
        Files.move(oldPath, Paths.get(newFilePath));
        serveChannel.write(ByteBuffer.wrap("S".getBytes()));
      } else {
        serveChannel.write(ByteBuffer.wrap("F".getBytes()));
      }
    } finally {
      lock.unlock();
      serveChannel.close();
    }
  }

  private static void handleDownload(
    SocketChannel serveChannel,
    ByteBuffer request
  ) throws IOException {
    int fileNameLength = request.getInt();
    byte[] g = new byte[fileNameLength];
    request.get(g);
    String fileName = new String(g, StandardCharsets.UTF_8);

    String filePath = BASE_PATH + File.separator + fileName;
    System.out.println(filePath);

    ReentrantLock lock = fileLocks.computeIfAbsent(
      filePath,
      k -> new ReentrantLock()
    );
    lock.lock();
    try {
      Path path = Paths.get(filePath);
      if (Files.exists(path) && Files.isRegularFile(path)) {
        byte[] fileContent = Files.readAllBytes(path);
        serveChannel.write(ByteBuffer.wrap(fileContent));
      } else {
        ByteBuffer notFound = ByteBuffer.wrap("F".getBytes());
        serveChannel.write(notFound);
      }
    } finally {
      lock.unlock();
      serveChannel.close();
    }
  }

  private static void handleUpload(
    SocketChannel serveChannel,
    ByteBuffer request
  ) throws IOException {
    int fileNameLength = request.getInt();
    byte[] u = new byte[fileNameLength];
    request.get(u);
    String fileName = new String(u, StandardCharsets.UTF_8);

    String filePath = BASE_PATH + File.separator + fileName;

    ReentrantLock lock = fileLocks.computeIfAbsent(
      filePath,
      k -> new ReentrantLock()
    );
    lock.lock();
    try {
      Path path = Paths.get(filePath);

      // Always send 'S' response, because we're always ready to receive the file content
      ByteBuffer fileExists = ByteBuffer.wrap("S".getBytes());
      serveChannel.write(fileExists);

      // Read the length of the file content from the request
      int fileContentLength = request.getInt();
      ByteBuffer fileContent = ByteBuffer.allocate(fileContentLength);

      // Read the file content from the request
      serveChannel.read(fileContent);
      fileContent.flip();

      // Write the file content to the file
      Files.write(
        path,
        fileContent.array(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      );
    } finally {
      lock.unlock();
      serveChannel.close();
    }
  }

  private static void handleList(SocketChannel serveChannel)
    throws IOException {
    File directory = new File(BASE_PATH);
    File[] files = directory.listFiles();

    if (files != null) {
      StringBuilder fileList = new StringBuilder();
      for (File fileInDirectory : files) {
        fileList.append(fileInDirectory.getName()).append("\n");
      }

      ByteBuffer fileListBuffer = ByteBuffer.wrap(
        fileList.toString().getBytes()
      );
      serveChannel.write(fileListBuffer);
    } else {
      ByteBuffer listCode = ByteBuffer.wrap("F".getBytes());
      serveChannel.write(listCode);
    }

    serveChannel.close();
  }

  private static void handleClientRequest(SocketChannel serveChannel)
    throws IOException {
    ByteBuffer request = ByteBuffer.allocate(1024);
    serveChannel.read(request);
    request.flip();

    char command = (char) request.get();

    switch (command) {
      case 'D':
        handleDelete(serveChannel, request);
        break;
      case 'R':
        handleRename(serveChannel, request);
        break;
      case 'G':
        handleDownload(serveChannel, request);
        break;
      case 'U':
        handleUpload(serveChannel, request);
        break;
      case 'L':
        handleList(serveChannel);
        break;
      default:
        System.out.println("Unknown command: " + command);
        break;
    }
  }
}

