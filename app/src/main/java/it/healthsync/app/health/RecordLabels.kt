package it.healthsync.app.health

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.Vo2MaxRecord

fun bodyTemperatureLocation(location: Int): String = when (location) {
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_ARMPIT -> "armpit"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FINGER -> "finger"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FOREHEAD -> "forehead"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH -> "mouth"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_RECTUM -> "rectum"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TEMPORAL_ARTERY -> "temporal_artery"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TOE -> "toe"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR -> "ear"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST -> "wrist"
    BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_VAGINA -> "vagina"
    else -> "unknown"
}

fun bloodPressurePosition(position: Int): String = when (position) {
    BloodPressureRecord.BODY_POSITION_STANDING_UP -> "standing"
    BloodPressureRecord.BODY_POSITION_SITTING_DOWN -> "sitting"
    BloodPressureRecord.BODY_POSITION_LYING_DOWN -> "lying"
    BloodPressureRecord.BODY_POSITION_RECLINING -> "reclining"
    else -> "unknown"
}

fun bloodPressureLocation(location: Int): String = when (location) {
    BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST -> "left_wrist"
    BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST -> "right_wrist"
    BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM -> "left_upper_arm"
    BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM -> "right_upper_arm"
    else -> "unknown"
}

fun glucoseSpecimen(source: Int): String = when (source) {
    BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "interstitial_fluid"
    BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "capillary_blood"
    BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> "plasma"
    BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> "serum"
    BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS -> "tears"
    BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "whole_blood"
    else -> "unknown"
}

fun glucoseRelationToMeal(relation: Int): String = when (relation) {
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "general"
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "fasting"
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "before_meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "after_meal"
    else -> "unknown"
}

fun vo2Method(method: Int): String = when (method) {
    Vo2MaxRecord.MEASUREMENT_METHOD_METABOLIC_CART -> "metabolic_cart"
    Vo2MaxRecord.MEASUREMENT_METHOD_HEART_RATE_RATIO -> "heart_rate_ratio"
    Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST -> "cooper_test"
    Vo2MaxRecord.MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST -> "multistage_fitness_test"
    Vo2MaxRecord.MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST -> "rockport_fitness_test"
    else -> "other"
}
