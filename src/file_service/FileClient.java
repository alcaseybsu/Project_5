package file_service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class FileClient {

  private static final int STATUS_CODE_LENGTH = 1;

  public static void main(String[] args) throws Exception {
    Scanner keyboard = new Scanner(System.in);

    if (args.length != 2) {
      System.out.println("Syntax: FileClient <ServerIP> <ServerPort>");
    }
    int serverPort = Integer.parseInt(args[1]);
    String command;
    do {
      System.out.println("Please type a command:");
      command = keyboard.nextLine().toUpperCase();
      switch (command) {
        case "D":
          {
            System.out.println("Please enter file name");
            String fileToDelete = keyboard.nextLine();
            ByteBuffer deleteRequest = ByteBuffer.wrap(
              (command + fileToDelete).getBytes()
            );
            SocketChannel channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(args[0], serverPort));
            channel.write(deleteRequest);
            channel.shutdownOutput();
            ByteBuffer dCode = ByteBuffer.allocate(STATUS_CODE_LENGTH);
            channel.read(dCode);
            //new
            channel.close();
            dCode.flip();
            byte[] d = new byte[STATUS_CODE_LENGTH];
            dCode.get(d);
            System.out.println(new String(d));
            break;
          }
        case "U":
          {
            System.out.println(
              "Please enter the file name to upload (located in 'caseUFiles' dir):"
            );
            String fileToUpload = keyboard.nextLine();
            String filePath = "caseUFiles/" + fileToUpload;

            try {
              byte[] fileContent = Files.readAllBytes(Paths.get(filePath));

              // prepare request buffer with command, filepath length, filepath, and file content
              ByteBuffer uploadRequest = ByteBuffer.allocate(
                Integer.BYTES + // Command length
                command.length() +
                Integer.BYTES + // File path length
                filePath.length() +
                fileContent.length
              );

              uploadRequest.put(command.getBytes());
              uploadRequest.putInt(filePath.length());
              uploadRequest.put(filePath.getBytes());
              uploadRequest.put(fileContent);

              // Flip the buffer before writing to the channel
              uploadRequest.flip();

              try (SocketChannel channel = SocketChannel.open()) {
                channel.connect(new InetSocketAddress(args[0], serverPort));
                channel.write(uploadRequest);
                channel.shutdownOutput();

                ByteBuffer uploadCode = ByteBuffer.allocate(STATUS_CODE_LENGTH);
                channel.read(uploadCode);
                channel.close();
                uploadCode.flip();

                byte[] responseCode = new byte[STATUS_CODE_LENGTH];
                uploadCode.get(responseCode);

                if ("S".equals(new String(responseCode))) {
                  System.out.println("S");
                } else {
                  System.out.println("F");
                }
              }
            } catch (IOException e) {
              System.out.println("Error reading the file: " + e.getMessage());
            }

            break;
          }
        // download a file
        case "G":
          {
            System.out.println("Please enter the file name:");
            String fileToDownload = keyboard.nextLine().trim();

            // Debugging output
            System.out.println(
              "Client: File name after trimming: '" + fileToDownload + "'"
            );

            // Specify the download location on the client side (user's desktop)
            String userDesktop =
              System.getProperty("user.home") + "\\Desktop\\";
            String downloadPath = userDesktop + fileToDownload;

            ByteBuffer downloadRequest = ByteBuffer.wrap(
              (command + fileToDownload).getBytes()
            );
            try (SocketChannel downloadChannel = SocketChannel.open()) {
              downloadChannel.connect(
                new InetSocketAddress(args[0], serverPort)
              );
              downloadChannel.write(downloadRequest);
              downloadChannel.shutdownOutput();

              ByteBuffer fileContent = ByteBuffer.allocate(2500); // Adjust the buffer size as needed
              int numBytes;
              do {
                numBytes = downloadChannel.read(fileContent);
              } while (numBytes >= 0);

              fileContent.flip();

              // Check if the file exists on the server
              byte[] checkFile = new byte[1];
              fileContent.get(checkFile);
              if ("F".equals(new String(checkFile))) {
                System.out.println("File not found on the server.");
              } else {
                // Save the file on the client side
                Files.write(Paths.get(downloadPath), fileContent.array());
                System.out.println(
                  "File downloaded successfully to: " + downloadPath
                );
              }
            }
            break;
          }
        case "R":
          {
            System.out.println("Please enter the current file name: ");
            String currentFileName = keyboard.nextLine();
            System.out.println("Please enter the new file name: ");
            String newFileName = keyboard.nextLine();

            // Print statement added
            System.out.println(
              "Client: Renaming file '" +
              currentFileName +
              "' to '" +
              newFileName +
              "'..."
            );

            // Calculate the total size needed for the buffer
            int totalSize =
              Integer.BYTES + // Command length
              command.length() +
              Integer.BYTES + // Current file name length
              currentFileName.length() +
              Integer.BYTES + // New file name length
              newFileName.length();

            // Prepare the request buffer with command and file names
            ByteBuffer renameRequest = ByteBuffer.allocate(totalSize);

            renameRequest.put(command.getBytes());
            renameRequest.putInt(currentFileName.length());
            renameRequest.put(currentFileName.getBytes());
            renameRequest.putInt(newFileName.length());
            renameRequest.put(newFileName.getBytes());

            // Flip the buffer before writing to the channel
            renameRequest.flip();

            try (SocketChannel channel = SocketChannel.open()) {
              channel.connect(new InetSocketAddress(args[0], serverPort));
              channel.write(renameRequest);
              channel.shutdownOutput();

              ByteBuffer renameCode = ByteBuffer.allocate(STATUS_CODE_LENGTH);
              channel.read(renameCode);
              channel.close();
              renameCode.flip();

              byte[] responseCode = new byte[STATUS_CODE_LENGTH];
              renameCode.get(responseCode);

              if ("S".equals(new String(responseCode))) {
                System.out.println("S");
              } else {
                System.out.println("F");
              }
            }

            break;
          }
        case "L":
          {
            ByteBuffer listRequest = ByteBuffer.wrap(command.getBytes());
            try (SocketChannel channel = SocketChannel.open()) {
              channel.connect(new InetSocketAddress(args[0], serverPort));
              channel.write(listRequest);
              channel.shutdownOutput();

              ByteBuffer fileListBuffer = ByteBuffer.allocate(2500); // Adjust the buffer size as needed
              int numBytes;
              do {
                numBytes = channel.read(fileListBuffer);
              } while (numBytes >= 0);

              fileListBuffer.flip();
              byte[] fileListBytes = new byte[fileListBuffer.remaining()];
              fileListBuffer.get(fileListBytes);

              String fileList = new String(
                fileListBytes,
                StandardCharsets.UTF_8
              );
              System.out.println("File List:\n" + fileList);
            }
            break;
          }
        default:
          if (!command.equals("Q")) {
            System.out.println("Invalid command!");
          }
      }
    } while (!command.equals("Q"));
    keyboard.close(); // Close the scanner
  }
}
