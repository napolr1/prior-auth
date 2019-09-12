package org.hl7.davinci.priorauth;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.hl7.davinci.priorauth.Endpoint.RequestType;

/**
 * The ClaimResponse endpoint to READ, SEARCH for, and DELETE ClaimResponses to
 * submitted claims.
 */
@RestController
@RequestMapping("/ClaimResponse")
public class ClaimResponseEndpoint {

  private static String uri;

  @GetMapping(value = "", produces = "application/fhir+json")
  public ResponseEntity<String> readClaimResponseJson(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    uri = request.getRequestURL().toString();
    return readClaimResponse(id, patient, status, RequestType.JSON);
  }

  @GetMapping(value = "", produces = "application/fhir+xml")
  public ResponseEntity<String> readClaimResponseXml(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    uri = request.getRequestURL().toString();
    return readClaimResponse(id, patient, status, RequestType.XML);
  }

  public ResponseEntity<String> readClaimResponse(String id, String patient, String status, RequestType requestType) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();

    // get the claim id from the claim response id
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    String claimId = App.getDB().readString(Database.CLAIM_RESPONSE, constraintMap, "claimId");

    // get the most recent claim id
    claimId = App.getDB().getMostRecentId(claimId);

    // get the most recent claim response
    constraintMap.clear();
    if (claimId == null) {
      // no claim was found, call on the Endpoint.read to return the proper error
      constraintMap.put("id", id);
    } else {
      constraintMap.put("claimId", claimId);
    }
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Database.CLAIM_RESPONSE, constraintMap, uri, requestType);
  }

  @DeleteMapping(value = "", produces = "application/fhir+json")
  public ResponseEntity<String> deleteClaimResponse(@RequestParam(name = "identifier") String id,
      @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM_RESPONSE, RequestType.JSON);
  }

  @DeleteMapping(value = "", produces = "application/fhir+xml")
  public ResponseEntity<String> deleteClaimResponseXml(@RequestParam(name = "identifier") String id,
      @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM_RESPONSE, RequestType.XML);
  }

}
