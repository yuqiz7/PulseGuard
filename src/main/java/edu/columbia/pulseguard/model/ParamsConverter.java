package edu.columbia.pulseguard.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Stores a metric's detection parameters as a JSON object in a single text column.
 */
@Converter
public class ParamsConverter implements AttributeConverter<Map<String, Double>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Double>> PARAMS_TYPE = new TypeReference<>() {};

  /**
   * Serializes the parameters to JSON, with keys in sorted order.
   *
   * @param attribute the parameters held by the entity; null is stored as an empty object
   * @return the JSON text stored in the database
   */
  @Override
  public String convertToDatabaseColumn(Map<String, Double> attribute) {
    if (attribute == null) {
      return "{}";
    }
    try {
      return MAPPER.writeValueAsString(new TreeMap<>(attribute));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize metric parameters", e);
    }
  }

  /**
   * Parses the JSON text stored in the database back into a parameter map.
   *
   * @param dbData the JSON text; null or blank yields an empty map
   * @return a mutable map of parameter names to values
   */
  @Override
  public Map<String, Double> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new HashMap<>();
    }
    try {
      return new HashMap<>(MAPPER.readValue(dbData, PARAMS_TYPE));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Stored metric parameters are not valid JSON", e);
    }
  }
}
