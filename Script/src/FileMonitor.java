import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.concurrent.TimeUnit;

public class FileMonitor {
    private static final String WATCH_FOLDER = "C:\\Users\\ethan\\OneDrive\\Documents\\Death Star";
    private static final String GIT_REPO_PATH = "C:\\Users\\ethan\\OneDrive\\Documents\\SexyDeathStarz\\WSU-WebDevelopment-1-CS-2800-Fall-24";

    public static void main(String[] args) throws IOException, InterruptedException {
        // WatchService for monitoring file system changes
        WatchService watchService = FileSystems.getDefault().newWatchService();
        Path watchPath = Paths.get(WATCH_FOLDER);
        watchPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

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

                    // If a new file is created or modified, copy it to the repository and push to
                    // GitHub
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        copyFileToRepo(absolutePath.toFile());
                        runGitCommands();
                    }
                }
                key.reset();
            }
        }
    }

    // Method to copy the new/modified file from the watch folder to the Git
    // repository
    private static void copyFileToRepo(File srcFile) throws IOException {
        Path destPath = Paths.get(GIT_REPO_PATH, srcFile.getName());
        Files.copy(srcFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Copied " + srcFile.getName() + " to the repository.");
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
            builder.command("git", "commit", "-m", "Auto-update from watcher folder").start().waitFor();

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
