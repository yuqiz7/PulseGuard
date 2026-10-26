package edu.columbia.pulseguard.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Unit tests for {@link ParamsConverter}.
 */
class ParamsConverterTest {

  private final ParamsConverter converter = new ParamsConverter();

  @Test
  void serializesWithSortedKeys() {
    String json = converter.convertToDatabaseColumn(Map.of("zThreshold", 3.0, "windowSize", 20.0));
    assertThat(json).isEqualTo("{\"windowSize\":20.0,\"zThreshold\":3.0}");
  }

  @Test
  void serializesNullAsEmptyObject() {
    assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
  }

  @Test
  void roundTripPreservesValues() {
    Map<String, Double> params = Map.of("lowerBound", -1.5, "upperBound", 40.0);
    String json = converter.convertToDatabaseColumn(params);
    assertThat(converter.convertToEntityAttribute(json)).isEqualTo(params);
  }

  @Test
  void parsesNullAndBlankAsEmptyMap() {
    assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
  }

  @Test
  void rejectsInvalidJson() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("{not json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not valid JSON");
  }
}
