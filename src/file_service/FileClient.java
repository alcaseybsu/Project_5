package file_service;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
    if (args.length != 2) {
      System.out.println("Syntax: FileClient <ServerIP> <ServerPort>");
      return;
    }
    int serverPort = Integer.parseInt(args[1]);
    String command = "";

    try (
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in)
      )
    ) {
      do {
        System.out.println("Please type a command:");
        command = reader.readLine().toUpperCase();
        switch (command) {
          ///////////////////////////////////////////////////////////////////
          case "D":
            System.out.println("Please enter file name");
            Scanner keyboard = new Scanner(System.in);
            String fileName = keyboard.nextLine();

            // Print statement added
            System.out.println("Client: Deleting file '" + fileName + "'...");

            ByteBuffer request = ByteBuffer.wrap(
              (command + fileName).getBytes()
            );

            SocketChannel channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(args[0], serverPort));
            channel.write(request);
            channel.shutdownOutput();

            ByteBuffer code = ByteBuffer.allocate(STATUS_CODE_LENGTH);
            channel.read(code);
            channel.close();
            code.flip();
            byte[] a = new byte[STATUS_CODE_LENGTH];
            code.get(a);

            // Print the success or failure message
            if ("S".equals(new String(a))) {
              System.out.println(
                "Client: File '" + fileName + "' deleted successfully."
              );
            } else {
              System.out.println(
                "Client: File '" +
                fileName +
                "' does not exist or couldn't be deleted."
              );
            }

            keyboard.close();
            break;
          /////////////////////////////////////////////////////////////////////////
          case "U":
            {
              System.out.println("Please enter file name to upload");
              keyboard = new Scanner(System.in);
              fileName = reader.readLine();

              // Read the file content
              System.out.println("Please enter the path of the file to upload");
              String filePath = reader.readLine();
              byte[] fileContent = Files.readAllBytes(Paths.get(filePath));

              // Inform the user about the upload in progress
              System.out.println(
                "Uploading '" + fileName + "'... Please wait."
              );

              // create a ByteBuffer for the request (command + fileName + fileContent)
              request =
                ByteBuffer.allocate(1 + fileName.length() + fileContent.length);
              request.put(command.getBytes(StandardCharsets.UTF_8));
              request.put(fileName.getBytes(StandardCharsets.UTF_8));
              request.put(fileContent);

              channel = SocketChannel.open();
              channel.connect(new InetSocketAddress(args[0], serverPort));
              channel.write(request);
              channel.shutdownOutput();

              code = ByteBuffer.allocate(STATUS_CODE_LENGTH);
              channel.read(code);
              channel.close();
              code.flip();
              byte[] responseCode = new byte[STATUS_CODE_LENGTH];
              code.get(responseCode);

              // Print the success or failure message
              if ("S".equals(new String(responseCode))) {
                System.out.println(
                  "File '" + fileName + "' uploaded successfully."
                );
              } else {
                System.out.println(
                  "Failed to upload the file. Check if the file exists and the path is valid."
                );
              }
              keyboard.close();
              break;
            }
          ///////////////////////////////////////////////////////////////////
          case "G":
            {
              System.out.println("Please enter the file name to download:");
              fileName = reader.readLine();

              // Inform the user about the download in progress
              System.out.println(
                "Downloading '" + fileName + "'... Please wait."
              );

              request = ByteBuffer.wrap((command + fileName).getBytes());
              channel = SocketChannel.open();
              channel.connect(new InetSocketAddress(args[0], serverPort));
              channel.write(request);
              channel.shutdownOutput();

              ByteBuffer responseCode = ByteBuffer.allocate(STATUS_CODE_LENGTH);
              channel.read(responseCode);

              if ("S".equals(new String(responseCode.array()))) {
                ByteBuffer fileContent = ByteBuffer.allocate(2500);
                int bytesRead;
                while ((bytesRead = channel.read(fileContent)) > 0) {
                  fileContent.flip();
                  byte[] data = new byte[bytesRead];
                  fileContent.get(data);
                  // Save the file content to a file on the client side
                  try (
                    FileOutputStream fos = new FileOutputStream(
                      "DownloadedFiles/" + fileName
                    )
                  ) {
                    fos.write(data);
                  }
                  fileContent.clear();
                }
                System.out.println(
                  "File '" + fileName + "' downloaded successfully."
                );
              } else {
                System.out.println(
                  "Failed to download the file. File may not exist on the server."
                );
              }

              channel.close();
              break;
            }
          ///////////////////////////////////////////////////////////////////
          case "R":
            {
              System.out.println("Please enter the current file name:");
              keyboard = new Scanner(System.in);
              String currentFileName = keyboard.nextLine();

              System.out.println("Please enter the new file name:");
              String newFileName = keyboard.nextLine();

              // Print statement added
              System.out.println(
                "Client: Renaming file '" +
                currentFileName +
                "' to '" +
                newFileName +
                "'..."
              );

              request = ByteBuffer.allocate(
                Character.BYTES +
                Integer.BYTES +
                currentFileName.length() +
                Integer.BYTES +
                newFileName.length()
              );

              request.putChar(command.charAt(0));
              request.putInt(currentFileName.length());
              request.put(currentFileName.getBytes());
              request.putInt(newFileName.length());
              request.put(newFileName.getBytes());
              request.flip();

              channel = SocketChannel.open();
              channel.connect(new InetSocketAddress(args[0], serverPort));
              channel.write(request);
              channel.shutdownOutput();

              code = ByteBuffer.allocate(STATUS_CODE_LENGTH);
              channel.read(code);
              channel.close();
              code.flip();

              byte[] responseCode = new byte[STATUS_CODE_LENGTH];
              code.get(responseCode);

              // Print the success or failure message
              if ("S".equals(new String(responseCode))) {
                System.out.println("Client: File renamed successfully.");
              } else {
                System.out.println(
                  "Client: Failed to rename the file. Check if the file exists and the new name is valid."
                );
              }

              break;
            }
          ///////////////////////////////////////////////////////////////////
          case "L":
            {
              request = ByteBuffer.wrap(command.getBytes());
              channel = SocketChannel.open();
              channel.connect(new InetSocketAddress(args[0], serverPort));
              channel.write(request);
              channel.shutdownOutput();

              ByteBuffer response = ByteBuffer.allocate(2500);
              channel.read(response);
              channel.close();
              response.flip();

              // Check the server's response
              byte[] responseBytes = new byte[response.remaining()];
              response.get(responseBytes);
              String responseString = new String(
                responseBytes,
                StandardCharsets.UTF_8
              );

              if ("S".equals(responseString)) {
                System.out.println("Files available on the server:");
                while (response.hasRemaining()) {
                  byte[] fileNameBytes = new byte[response.get()];
                  response.get(fileNameBytes);
                  fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                  System.out.println(fileName);
                }
              } else {
                System.out.println(
                  "Failed to retrieve the file list from the server."
                );
              }
              break;
              ///////////////////////////////////////////////////////////////////
            }
          default:
            if (!command.equals("Q")) {
              System.out.println("Invalid command!");
            }
        }
      } while (!command.equals("Q"));
    } catch (IOException e) {
      e.printStackTrace(); // Handle the exception as needed
    }
  }
}
