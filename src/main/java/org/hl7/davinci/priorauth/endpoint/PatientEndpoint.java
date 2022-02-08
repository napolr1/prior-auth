package org.hl7.davinci.priorauth.endpoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;

/**
 * The Patient endpoint to READ, SEARCH for, and DELETE submitted patients.
 */
@CrossOrigin
@RestController
@RequestMapping("/Patient")
public class PatientEndpoint {

  static final Logger logger = PALogger.getLogger();
  // JSON output
  @GetMapping(value = {"", "/{id}"}, produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> readPatientJson(HttpServletRequest request,
      @PathVariable(required = false) String id,
      @RequestParam(name = "identifier", required = false) String patient) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    return Endpoint.read(Table.PATIENT, constraintMap, request, RequestType.JSON);
  }
  
  // XML output
  @GetMapping(value = {"", "/{id}"}, produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> readPatientXml(HttpServletRequest request,
      @PathVariable(required = false) String id,
      @RequestParam(name = "identifier", required = false) String patient) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    return Endpoint.read(Table.PATIENT, constraintMap, request, RequestType.XML);
  }
 
  @CrossOrigin
  @DeleteMapping(value = "/{id}", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> deleteBundle(HttpServletRequest request, @PathVariable String id,
      @RequestParam(name = "identifier", required = false) String patient) {
    return Endpoint.delete(id, patient, Table.PATIENT, request, RequestType.JSON);
  }

  @CrossOrigin
  @DeleteMapping(value = "/{id}", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> deleteBundleXml(HttpServletRequest request,
      @PathVariable String id, @RequestParam(name = "identifier", required = false) String patient) {
    return Endpoint.delete(id, patient, Table.PATIENT, request, RequestType.XML);
  }
}

