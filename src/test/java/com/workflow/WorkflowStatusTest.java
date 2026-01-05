package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for WorkflowStatus enum. */
class WorkflowStatusTest {

  @Test
  void allStatuses_exist() {
    WorkflowStatus[] statuses = WorkflowStatus.values();

    assertEquals(3, statuses.length);
    assertTrue(Arrays.asList(statuses).contains(WorkflowStatus.SUCCESS));
    assertTrue(Arrays.asList(statuses).contains(WorkflowStatus.FAILED));
    assertTrue(Arrays.asList(statuses).contains(WorkflowStatus.SKIPPED));
  }

  @Test
  void valueOf_validNames_returnCorrectStatus() {
    assertEquals(WorkflowStatus.SUCCESS, WorkflowStatus.valueOf("SUCCESS"));
    assertEquals(WorkflowStatus.FAILED, WorkflowStatus.valueOf("FAILED"));
    assertEquals(WorkflowStatus.SKIPPED, WorkflowStatus.valueOf("SKIPPED"));
  }

  @Test
  void valueOf_invalidName_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> WorkflowStatus.valueOf("INVALID"));
    assertThrows(IllegalArgumentException.class, () -> WorkflowStatus.valueOf("success"));
    assertThrows(IllegalArgumentException.class, () -> WorkflowStatus.valueOf("Failed"));
  }

  @Test
  void valueOf_null_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> WorkflowStatus.valueOf(null));
  }

  @Test
  void name_returnsCorrectString() {
    assertEquals("SUCCESS", WorkflowStatus.SUCCESS.name());
    assertEquals("FAILED", WorkflowStatus.FAILED.name());
    assertEquals("SKIPPED", WorkflowStatus.SKIPPED.name());
  }

  @Test
  void toString_returnsName() {
    assertEquals("SUCCESS", WorkflowStatus.SUCCESS.toString());
    assertEquals("FAILED", WorkflowStatus.FAILED.toString());
    assertEquals("SKIPPED", WorkflowStatus.SKIPPED.toString());
  }

  @Test
  void ordinal_isConsistent() {
    assertEquals(0, WorkflowStatus.SUCCESS.ordinal());
    assertEquals(1, WorkflowStatus.FAILED.ordinal());
    assertEquals(2, WorkflowStatus.SKIPPED.ordinal());
  }

  @Test
  void equals_differentStatuses_returnsFalse() {
    assertNotEquals(WorkflowStatus.SUCCESS, WorkflowStatus.FAILED);
    assertNotEquals(WorkflowStatus.FAILED, WorkflowStatus.SKIPPED);
    assertNotEquals(WorkflowStatus.SKIPPED, WorkflowStatus.SUCCESS);
  }

  @Test
  void hashCode_isConsistent() {
    assertEquals(WorkflowStatus.SUCCESS.hashCode(), WorkflowStatus.SUCCESS.hashCode());
    assertEquals(WorkflowStatus.FAILED.hashCode(), WorkflowStatus.FAILED.hashCode());
    assertEquals(WorkflowStatus.SKIPPED.hashCode(), WorkflowStatus.SKIPPED.hashCode());
  }

  @Test
  void canBeUsedAsMapKey() {
    Map<WorkflowStatus, String> map = new EnumMap<>(WorkflowStatus.class);
    map.put(WorkflowStatus.SUCCESS, "All good");
    map.put(WorkflowStatus.FAILED, "Error occurred");
    map.put(WorkflowStatus.SKIPPED, "Not executed");

    assertEquals("All good", map.get(WorkflowStatus.SUCCESS));
    assertEquals("Error occurred", map.get(WorkflowStatus.FAILED));
    assertEquals("Not executed", map.get(WorkflowStatus.SKIPPED));
  }

  @Test
  void values_returnsNewArray() {
    WorkflowStatus[] array1 = WorkflowStatus.values();
    WorkflowStatus[] array2 = WorkflowStatus.values();

    assertNotSame(array1, array2);
    assertArrayEquals(array1, array2);
  }

  @Test
  void values_canBeModified_withoutAffectingEnum() {
    WorkflowStatus[] values = WorkflowStatus.values();
    values[0] = null;

    // Original enum is not affected
    assertNotNull(WorkflowStatus.SUCCESS);
    assertNotNull(WorkflowStatus.values()[0]);
  }

  @Test
  void serialization_preservesIdentity() throws Exception {
    // Test that serialization/deserialization preserves enum identity
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(WorkflowStatus.SUCCESS);
    oos.close();

    ByteArrayInputStream bais = new ByteArrayInputStream(bos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);
    WorkflowStatus deserialized = (WorkflowStatus) ois.readObject();

    assertSame(WorkflowStatus.SUCCESS, deserialized);
  }

  @Test
  void getDeclaringClass_returnsWorkflowStatus() {
    assertEquals(WorkflowStatus.class, WorkflowStatus.SUCCESS.getDeclaringClass());
    assertEquals(WorkflowStatus.class, WorkflowStatus.FAILED.getDeclaringClass());
    assertEquals(WorkflowStatus.class, WorkflowStatus.SKIPPED.getDeclaringClass());
  }

  @Test
  void enumConstants_canBeIterated() {
    int count = 0;
    for (WorkflowStatus status : WorkflowStatus.values()) {
      assertNotNull(status);
      count++;
    }
    assertEquals(3, count);
  }

  @Test
  void statusInList_canBeChecked() {
    List<WorkflowStatus> successStatuses = List.of(WorkflowStatus.SUCCESS);
    List<WorkflowStatus> terminalStatuses =
        Arrays.asList(WorkflowStatus.SUCCESS, WorkflowStatus.FAILED);

    assertTrue(successStatuses.contains(WorkflowStatus.SUCCESS));
    assertFalse(successStatuses.contains(WorkflowStatus.FAILED));
    assertTrue(terminalStatuses.contains(WorkflowStatus.SUCCESS));
    assertTrue(terminalStatuses.contains(WorkflowStatus.FAILED));
  }
}
