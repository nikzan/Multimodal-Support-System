package com.nova.support.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Конвертер для PostgreSQL vector типа
 * Преобразует float[] в строковое представление вектора для pgvector
 */
@Converter(autoApply = true)
public class VectorAttributeConverter implements AttributeConverter<float[], String> {
    
    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        
        // Преобразуем float[] в строку формата "[1.0,2.0,3.0]"
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(attribute[i]);
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        
        // Удаляем скобки и разбиваем по запятым
        String cleaned = dbData.replaceAll("[\\[\\]]", "");
        String[] parts = cleaned.split(",");
        
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        
        return result;
    }
}
