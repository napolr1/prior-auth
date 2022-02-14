package org.hl7.davinci.priorauth.endpoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.authorization.AuthUtils;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import ca.uhn.fhir.parser.IParser;

/**
 * The Patient endpoint to READ, SEARCH for, and DELETE matchted patients.
 */
@CrossOrigin
@RestController
@RequestMapping("/Patient")
public class PatientEndpoint {

  static final Logger logger = PALogger.getLogger();
  static final String REQUIRES_PARAMETERS = "Patient matching Patient/$match Operation requires a Parameters resource containing a single Patient resource in parameter field.";
  static final String REQUIRES_PATIENT = "Patient resource must be the first element of Parameters.parameter";
  static final String REQUIRES_MIN_CRITERIA = "The request does not conform to the specification. Patient resource does not have the minimum search field";
  static final String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";
  static final String BASE_PROFILE = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient";
  static final String LEVEL1_PROFILE = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L1";
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

  @PostMapping(value = "/$match", consumes = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> matchOperationJson(HttpServletRequest request, HttpEntity<String> entity) {
    return matchOperation(entity.getBody(), RequestType.JSON, request);
  }

  @PostMapping(value = "/$match", consumes = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> matchOperationXml(HttpServletRequest request, HttpEntity<String> entity) {
    return matchOperation(entity.getBody(), RequestType.XML, request);
  }
   /**
   * The matchOperation ($match) function for both json and xml
   * 
   * @param body        - the body of the post request.
   * @param requestType - the RequestType of the request.
   * @return - Bundle containing a set of possible matching Patient entries
   */
  private ResponseEntity<String> matchOperation(String body, RequestType requestType, HttpServletRequest request) {
    logger.info("POST /Patient/$match fhir+" + requestType.name());
    App.setBaseUrl(Endpoint.getServiceBaseUrl(request));

    // TODO: check it is conforming to UDAP workflow
    if (!AuthUtils.validateAccessToken(request))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
          .body("{ error: \"Invalid access token. Make sure to use Authorization: Bearer (token)\" }");

    HttpStatus status = HttpStatus.BAD_REQUEST;
    String formattedData = null;
    AuditEventOutcome auditOutcome = AuditEventOutcome.MINOR_FAILURE;

    try {
      IParser parser = requestType == RequestType.JSON ? App.getFhirContext().newJsonParser()
          : App.getFhirContext().newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      logger.info("PatientEndpoint::MatchOperationParsedInput: " + resource.toString());
      // First check if the input is a Parameters resource
      if (resource instanceof Parameters) {
        Parameters parameters = (Parameters) resource;
        // Check if the patient resource is provided in the parameter field with patient resource
        if (parameters.hasParameter() && (!parameters.getParameter().isEmpty()) && parameters.getParameter().get(0).hasResource() 
          && parameters.getParameter().get(0).getResource().getResourceType() == ResourceType.Patient) {
            Patient patient = (Patient) parameters.getParameter().get(0).getResource();
            // Validate the minimum search criteria.
            if (validateMinimumRequirement(patient)) {
              // logic for match
            } else {
              OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_MIN_CRITERIA);
              formattedData = FhirUtils.getFormattedData(error, requestType);
              logger.severe("PatientEndpoint::ValidateMinimumRequirement:First Patient does not have the minimum required field");
            }
            // TODO: once the minimum requirement is validated, define the strategy to identify possible match and scoring
            /**
             * compare the given patient resouce fields to each pt resource from the DB
             * Possible logic:
             * select patient resource with at least 2 matching fields??
             * * possible fuzzy match on each field (name, address, DOB, etc)???
             * scoring: weight on each field ??
             */
        } else {
          // Patient is required...
          OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_PATIENT);
          formattedData = FhirUtils.getFormattedData(error, requestType);
          logger.severe("PatientEndpoint::MatchOperation:First Parameters parameter is not a Patient");
        }
      } else {
        // Input should be a Parameters resource...
        OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_PARAMETERS);
        formattedData = FhirUtils.getFormattedData(error, requestType);
        logger.severe("PatientEndpoint::MatchOperation:Body is not a Parameters resource");
      }
    } catch (Exception e) {
      // Spectacular faillure of the match request
      OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      formattedData = FhirUtils.getFormattedData(error, requestType);
      auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
    }
    Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, auditOutcome, null, request, "POST /Patient/$match");
    MediaType contentType = requestType == RequestType.JSON ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML;
    String fhirContentType = requestType == RequestType.JSON ? "application/fhir+json" : "application/xml+json";
    return ResponseEntity.status(status).contentType(contentType)
        .header(HttpHeaders.CONTENT_TYPE, fhirContentType + "; charset=utf-8")
        .header(HttpHeaders.LOCATION,
            App.getBaseUrl() + "/Patient")
        .body(formattedData);
  }

  /**
   * Validates the patient resource provided in the Patient/$match request meet the minimum
   * requirement of the profile it claims to be conformant to.
   * 
   * @param patient - the patient resource contained in the Parameters resource provided in the post request.
   * @return - true if constraints are validated, false otherwise
   */
  private boolean validateMinimumRequirement(Patient patient) {
    String profile = BASE_PROFILE;
    if(patient.hasMeta() && patient.getMeta().hasProfile() && !patient.getMeta().getProfile().isEmpty())
      profile = patient.getMeta().getProfile().get(0).getValue();
    Map<String, Object> fullName = FhirUtils.getPatientFirstAndLastName(patient);
    Boolean hasFullName = (fullName.get("firstName") != null) && (fullName.get("lastName") != null);
    Boolean hasHomeAddress = FhirUtils.getPatientHomeAddress(patient) != null;
    Boolean validated = false;
    try {
      if (profile.equals(BASE_PROFILE)) {
        // Base level constraints
        validated = patient.hasIdentifier() || patient.hasTelecom() || hasFullName ||hasHomeAddress || patient.hasBirthDate();
      } else {
        int weight = totalWeigh(patient);

        if (profile.equals(LEVEL1_PROFILE)) {
          validated = weight >= 6;
        } else {
          validated = weight >= 12;
        }
  
      }
    } catch (Exception e) {
      logger.severe("PatientEndpoint::ValidateMinimumRequirement: Validation failed with " + e.getMessage());
    }
    return validated;
  }

  /**
   * Internal method to calculate the weight of the $match operation search criteria
   * @param patient - the provided patient resource from the post query
   * @return the total weight of the search fields
   */
  private int totalWeigh(Patient patient) {
    Map<String, Object> fullName = FhirUtils.getPatientFirstAndLastName(patient);
    Boolean hasFullName = (fullName.get("firstName") != null) && (fullName.get("lastName") != null);

    int ppnWeight = FhirUtils.getPasportNumFromPatient(patient) != null ? 9 : 0;
    int dlWeight = FhirUtils.getDLNumFromPatient(patient) != null ? 9 : 0;
    int mobileWeight = FhirUtils.getPatientMobilePhone(patient) != null ? 7 : 0;
    int emailWeight = FhirUtils.getPatientEmail(patient) != null ? 7 : 0; 
    int nameWeight = hasFullName ? 5 : 0;
    int addressWeight = FhirUtils.getPatientHomeAddress(patient) != null ? 5 : 0;
    int birthDateWeight = patient.hasBirthDate() ? 2 : 0;
    int maritalStatusWeight = FhirUtils.getPatientMaritalStatus(patient) != null ? 1 : 0;
    int genderWeight = patient.hasGender() ? 1 : 0;

    return ppnWeight + dlWeight + mobileWeight + emailWeight + nameWeight + addressWeight + birthDateWeight
          + maritalStatusWeight + genderWeight;
  }

}

