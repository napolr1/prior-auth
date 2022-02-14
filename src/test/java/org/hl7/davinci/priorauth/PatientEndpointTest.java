package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.validation.ValidationResult;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "server.servlet.contextPath=/fhir")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class PatientEndpointTest {
  
  @LocalServerPort
  private int port;

  @Autowired
  private WebApplicationContext wac;

  private static ResultMatcher cors = MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "*");
  private static ResultMatcher ok = MockMvcResultMatchers.status().isOk();
  private static ResultMatcher notFound = MockMvcResultMatchers.status().isNotFound();

  @BeforeClass
  public static void setup() throws FileNotFoundException {
    App.initializeAppDB();

    // Create a single test Patient
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("patient-only.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Patient patient = (Patient) App.getFhirContext().newJsonParser().parseResource(inputStream);
    Map<String, Object> patientMap = new HashMap<String, Object>();
    patientMap.put("id", "pat013");
    patientMap.put("resource", patient);
    App.getDB().write(Table.PATIENT, patientMap);
  }

  @AfterClass
  public static void cleanup() {
    App.getDB().delete(Table.PATIENT, "pat013", null);
  }

  @Test
  public void searchPatients() throws Exception {
    // Test that we can GET /fhir/Patient.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/Patient")
        .header("Accept", "application/fhir+json").header("Access-Control-Request-Method", "GET")
        .header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a JSON Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Bundle bundle = (Bundle) App.getFhirContext().newJsonParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void searchPatientsXml() throws Exception {
    // Test that we can GET /fhir/Patient.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/Patient")
        .header("Accept", "application/fhir+xml").header("Access-Control-Request-Method", "GET")
        .header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a XML Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Bundle bundle = (Bundle) App.getFhirContext().newXmlParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void patientExists() {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", "pat013");
    Patient patient = (Patient) App.getDB().read(Table.PATIENT, constraintMap);
    Assert.assertNotNull(patient);
  }

  @Test
  public void getPatient() throws Exception {
    // Test that we can GET /fhir/Patient/pat013.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
        .get("/Patient/pat013").header("Accept", "application/fhir+json")
        .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a JSON Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Patient patient = (Patient) App.getFhirContext().newJsonParser().parseResource(body);
    Assert.assertNotNull(patient);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(patient);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getPatientXml() throws Exception {
    // Test that we can GET /fhir/Patient/pat013.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
        .get("/Patient/pat013").header("Accept", "application/fhir+xml")
        .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a XML Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Patient patient = (Patient) App.getFhirContext().newXmlParser().parseResource(body);
    Assert.assertNotNull(patient);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(patient);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getPatientThatDoesNotExist() throws Exception {
    // Test that non-existent Patient returns 404.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
        .get("/Patient/PatientThatDoesNotExist").header("Accept", "application/fhir+json")
        .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 404
    mockMvc.perform(requestBuilder).andExpect(notFound).andExpect(cors);
  }


}
