import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PropertiesParserTest {
    
    public static void main(String[] args) {
        System.out.println("=== Starting PropertiesParser Tests ===");
        
        testNormalParsing();
        testErrorHandling();
        testEmptyFile();
        testFileNotFound();
        
        System.out.println("\n=== All tests completed ===");
    }
    
    private static void testNormalParsing() {
        System.out.println("\n--- Test 1: Normal configuration file parsing ---");
        
        try {
            Path tempFile = Files.createTempFile("normal_test", ".properties");
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()));
            
            writer.write("# This is a comment line\n");
            writer.write("\n");
            writer.write("db.host=localhost\n");
            writer.write("db.port=3306\n");
            writer.write("db.name=testdb\n");
            writer.write("\n");
            writer.write("# Another comment\n");
            writer.write("app.name=TestApp\n");
            writer.write("app.version=1.0.0\n");
            
            writer.close();
            
            PropertiesParser parser = new PropertiesParser();
            parser.parse(tempFile.toString());
            
            boolean passed = true;
            
            if (parser.getTotalLines() != 9) {
                System.out.println("FAILED: Total lines should be 9, actual: " + parser.getTotalLines());
                passed = false;
            }
            
            if (parser.getValidLines() != 5) {
                System.out.println("FAILED: Valid items should be 5, actual: " + parser.getValidLines());
                passed = false;
            }
            
            if (parser.getCommentLines() != 2) {
                System.out.println("FAILED: Comment lines should be 2, actual: " + parser.getCommentLines());
                passed = false;
            }
            
            if (parser.getEmptyLines() != 2) {
                System.out.println("FAILED: Empty lines should be 2, actual: " + parser.getEmptyLines());
                passed = false;
            }
            
            Map<String, String> properties = parser.getProperties();
            if (!"localhost".equals(properties.get("db.host"))) {
                System.out.println("FAILED: db.host value is incorrect");
                passed = false;
            }
            
            if (!"3306".equals(properties.get("db.port"))) {
                System.out.println("FAILED: db.port value is incorrect");
                passed = false;
            }
            
            if (!"testdb".equals(properties.get("db.name"))) {
                System.out.println("FAILED: db.name value is incorrect");
                passed = false;
            }
            
            if (!"TestApp".equals(properties.get("app.name"))) {
                System.out.println("FAILED: app.name value is incorrect");
                passed = false;
            }
            
            if (!"1.0.0".equals(properties.get("app.version"))) {
                System.out.println("FAILED: app.version value is incorrect");
                passed = false;
            }
            
            if (!parser.getErrors().isEmpty()) {
                System.out.println("FAILED: Should have no errors, but found " + parser.getErrors().size() + " errors");
                passed = false;
            }
            
            if (passed) {
                System.out.println("SUCCESS: Normal configuration file parsing test passed");
            }
            
            Files.delete(tempFile);
            
        } catch (IOException e) {
            System.out.println("FAILED: Exception during test - " + e.getMessage());
        }
    }
    
    private static void testErrorHandling() {
        System.out.println("\n--- Test 2: Error handling test ---");
        
        try {
            Path tempFile = Files.createTempFile("error_test", ".properties");
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()));
            
            writer.write("valid.key=validValue\n");
            writer.write("duplicate.key=firstValue\n");
            writer.write("duplicate.key=secondValue\n");
            writer.write("empty.value=\n");
            writer.write("invalid line without equals\n");
            writer.write("=value without key\n");
            writer.write("another.valid=anotherValue\n");
            
            writer.close();
            
            PropertiesParser parser = new PropertiesParser();
            parser.parse(tempFile.toString());
            
            boolean passed = true;
            
            if (parser.getValidLines() != 2) {
                System.out.println("FAILED: Valid items should be 2, actual: " + parser.getValidLines());
                passed = false;
            }
            
            List<String> errors = parser.getErrors();
            if (errors.size() != 4) {
                System.out.println("FAILED: Should have 4 errors, actual: " + errors.size());
                System.out.println("Error list:");
                for (String error : errors) {
                    System.out.println("  - " + error);
                }
                passed = false;
            }
            
            boolean hasDuplicateError = false;
            boolean hasEmptyValueError = false;
            boolean hasNoEqualsError = false;
            boolean hasEmptyKeyError = false;
            
            for (String error : errors) {
                if (error.contains("duplicate key") && error.contains("duplicate.key")) {
                    hasDuplicateError = true;
                }
                if (error.contains("value is empty") && error.contains("empty.value")) {
                    hasEmptyValueError = true;
                }
                if (error.contains("missing equals sign")) {
                    hasNoEqualsError = true;
                }
                if (error.contains("key cannot be empty")) {
                    hasEmptyKeyError = true;
                }
            }
            
            if (!hasDuplicateError) {
                System.out.println("FAILED: Duplicate key error not detected");
                passed = false;
            }
            
            if (!hasEmptyValueError) {
                System.out.println("FAILED: Empty value error not detected");
                passed = false;
            }
            
            if (!hasNoEqualsError) {
                System.out.println("FAILED: Missing equals sign error not detected");
                passed = false;
            }
            
            if (!hasEmptyKeyError) {
                System.out.println("FAILED: Empty key error not detected");
                passed = false;
            }
            
            Map<String, String> properties = parser.getProperties();
            if (!"validValue".equals(properties.get("valid.key"))) {
                System.out.println("FAILED: valid.key value is incorrect");
                passed = false;
            }
            
            if (!"anotherValue".equals(properties.get("another.valid"))) {
                System.out.println("FAILED: another.valid value is incorrect");
                passed = false;
            }
            
            if (passed) {
                System.out.println("SUCCESS: Error handling test passed");
            }
            
            Files.delete(tempFile);
            
        } catch (IOException e) {
            System.out.println("FAILED: Exception during test - " + e.getMessage());
        }
    }
    
    private static void testEmptyFile() {
        System.out.println("\n--- Test 3: Empty file test ---");
        
        try {
            Path tempFile = Files.createTempFile("empty_test", ".properties");
            
            PropertiesParser parser = new PropertiesParser();
            parser.parse(tempFile.toString());
            
            boolean passed = true;
            
            if (parser.getTotalLines() != 0) {
                System.out.println("FAILED: Total lines should be 0, actual: " + parser.getTotalLines());
                passed = false;
            }
            
            if (parser.getValidLines() != 0) {
                System.out.println("FAILED: Valid items should be 0, actual: " + parser.getValidLines());
                passed = false;
            }
            
            if (parser.getCommentLines() != 0) {
                System.out.println("FAILED: Comment lines should be 0, actual: " + parser.getCommentLines());
                passed = false;
            }
            
            if (parser.getEmptyLines() != 0) {
                System.out.println("FAILED: Empty lines should be 0, actual: " + parser.getEmptyLines());
                passed = false;
            }
            
            if (!parser.getProperties().isEmpty()) {
                System.out.println("FAILED: Properties map should be empty");
                passed = false;
            }
            
            if (!parser.getErrors().isEmpty()) {
                System.out.println("FAILED: Should have no errors");
                passed = false;
            }
            
            if (passed) {
                System.out.println("SUCCESS: Empty file test passed");
            }
            
            Files.delete(tempFile);
            
        } catch (IOException e) {
            System.out.println("FAILED: Exception during test - " + e.getMessage());
        }
    }
    
    private static void testFileNotFound() {
        System.out.println("\n--- Test 4: File not found test ---");
        
        PropertiesParser parser = new PropertiesParser();
        parser.parse("non_existent_file.properties");
        
        boolean passed = true;
        
        if (parser.getErrors().isEmpty()) {
            System.out.println("FAILED: Should detect file not found error");
            passed = false;
        } else {
            String firstError = parser.getErrors().get(0);
            if (!firstError.contains("Error reading file")) {
                System.out.println("FAILED: Error message is incorrect");
                passed = false;
            }
        }
        
        if (passed) {
            System.out.println("SUCCESS: File not found test passed");
        }
    }
}
