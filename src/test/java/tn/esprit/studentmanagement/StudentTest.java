package tn.esprit.studentmanagement;

import org.junit.jupiter.api.Test;
import tn.esprit.studentmanagement.entities.Student;
import static org.junit.jupiter.api.Assertions.*;

class StudentTest {

    @Test
    void testStudentLombok() {
        // 1. Test No-Args Constructor
        Student s1 = new Student();

        // 2. Test Setters (Must match your Entity field names!)
        s1.setIdStudent(1L);          // Was setId
        s1.setFirstName("Skander");   // Was setName
        s1.setLastName("Ferjani");    // Added lastName
        s1.setEmail("skander@test.com");

        // 3. Test Getters
        assertEquals(1L, s1.getIdStudent());
        assertEquals("Skander", s1.getFirstName());
        assertEquals("Ferjani", s1.getLastName());
        assertEquals("skander@test.com", s1.getEmail());

        // 4. Test toString
        assertNotNull(s1.toString());

        // 5. Test Equals/HashCode
        Student s2 = new Student();
        s2.setIdStudent(1L);
        s2.setFirstName("Skander");
        s2.setLastName("Ferjani");
        s2.setEmail("skander@test.com");

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}