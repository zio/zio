import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RunVerification {
    public static void main(String[] args) {
        System.out.println("=== ZIO Race Optimization Verification ===\n");
        System.out.println("Running the verification script to test if our optimized race implementation");
        System.out.println("meets the 5x performance goal compared to cats-effect.\n");
        
        try {
            // Build the command to run the Scala script
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add("-jar");
            command.add(findScalaJar());
            command.add("VerifyRaceOptimization.scala");
            
            // Create process builder
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            // Start the process
            System.out.println("Executing: " + String.join(" ", command));
            Process process = processBuilder.start();
            
            // Read output
            java.io.InputStream is = process.getInputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            // Wait for the process to complete
            boolean completed = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!completed) {
                System.out.println("\nProcess timed out after 5 minutes");
                process.destroy();
            } else {
                int exitCode = process.exitValue();
                System.out.println("\nProcess completed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            System.out.println("\nError running verification script: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String findScalaJar() throws IOException {
        // Try to find scala-library.jar in common locations
        String[] possiblePaths = {
            "/usr/local/share/scala/lib/scala-library.jar",
            "/usr/share/scala/lib/scala-library.jar",
            System.getProperty("user.home") + "/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.13.10.jar",
            System.getProperty("user.home") + "/.m2/repository/org/scala-lang/scala-library/2.13.10/scala-library-2.13.10.jar"
        };
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return path;
            }
        }
        
        throw new IOException("Could not find scala-library.jar. Please specify the path manually.");
    }
}