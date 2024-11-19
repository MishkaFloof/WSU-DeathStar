import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class FileMonitor {
    private static final String WATCH_FOLDER = "C:\\Users\\ethan\\OneDrive\\Documents\\Death Star";
    private static final String GIT_REPO_PATH = "C:\\Users\\ethan\\OneDrive\\Documents\\SexyDeathStarz\\RobertsHell.github.io";
    private static final String INDEX_HTML = GIT_REPO_PATH + "\\index.html";

    public static void main(String[] args) throws IOException, InterruptedException {
        // WatchService for monitoring file system changes
        WatchService watchService = FileSystems.getDefault().newWatchService();
        Path watchPath = Paths.get(WATCH_FOLDER);
        watchPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        System.out.println("Monitoring folder for changes...");

        // Continuously monitor the folder
        while (true) {
            WatchKey key = watchService.poll(1, TimeUnit.SECONDS);

            if (key != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path filePath = (Path) event.context();
                    Path absolutePath = watchPath.resolve(filePath);
                    System.out.println(kind.name() + ": " + absolutePath);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // Copy the file to the repository
                        copyFileToRepo(absolutePath.toFile());
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        // Delete the file from the repository
                        deleteFileFromRepo(filePath.getFileName().toString());
                    }

                    // Update only the gallery section in the index.html file
                    updateGalleryInMainHtml();

                    // Push changes to GitHub
                    runGitCommands();
                }
                key.reset();
            }
        }
    }

    // Method to copy the new/modified file from the watch folder to the Git
    // repository
    private static void copyFileToRepo(File srcFile) throws IOException {
        Path destPath = Paths.get(GIT_REPO_PATH, "images", srcFile.getName());
        Files.createDirectories(destPath.getParent()); // Ensure images folder exists
        Files.copy(srcFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Copied " + srcFile.getName() + " to the repository.");
    }

    // Method to delete a file from the Git repository
    private static void deleteFileFromRepo(String fileName) throws IOException {
        Path destPath = Paths.get(GIT_REPO_PATH, "images", fileName);
        Files.deleteIfExists(destPath);
        System.out.println("Deleted " + fileName + " from the repository.");
    }

    // Method to update only the gallery section in the index.html file
    private static void updateGalleryInMainHtml() throws IOException {
        Path imagesFolder = Paths.get(GIT_REPO_PATH, "images");
        Path mainHtmlPath = Paths.get(GIT_REPO_PATH, "main.html");

        // Read the original main.html
        String htmlContent = Files.readString(mainHtmlPath);

        // Locate the gallery placeholder (e.g., <div class="gallery">...</div>)
        String galleryStartTag = "<div class=\"gallery\">";
        String galleryEndTag = "</div>";

        int startIndex = htmlContent.indexOf(galleryStartTag) + galleryStartTag.length();
        int endIndex = htmlContent.indexOf(galleryEndTag, startIndex);

        if (startIndex == -1 || endIndex == -1) {
            System.err.println("Gallery placeholder not found in main.html.");
            return;
        }

        // Generate <img> tags for all images in the images folder
        StringBuilder galleryContent = new StringBuilder();
        Files.list(imagesFolder).forEach(imagePath -> {
            if (Files.isRegularFile(imagePath)) {
                String imageName = imagesFolder.relativize(imagePath).toString();
                galleryContent.append("<img src=\"images/").append(imageName).append("\" alt=\"").append(imageName)
                        .append("\">\n");
            }
        });

        // Replace the content inside the gallery placeholder
        String updatedHtmlContent = htmlContent.substring(0, startIndex) +
                "\n" + galleryContent.toString() +
                htmlContent.substring(endIndex);

        // Write the updated content back to main.html
        Files.writeString(mainHtmlPath, updatedHtmlContent, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Updated gallery in main.html.");
    }

    // Method to run the Git commands (add, commit, push)
    private static void runGitCommands() {
        try {
            // Change directory to the repository
            ProcessBuilder builder = new ProcessBuilder();
            builder.directory(new File(GIT_REPO_PATH));

            // Add changes
            builder.command("git", "add", ".").start().waitFor();

            // Commit changes
            builder.command("git", "commit", "-m", "Auto-update gallery in index.html").start().waitFor();

            // Push changes
            Process pushProcess = builder.command("git", "push", "origin", "main").start(); // Adjust branch as needed
            BufferedReader reader = new BufferedReader(new InputStreamReader(pushProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            pushProcess.waitFor();

            System.out.println("Changes pushed to GitHub.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
