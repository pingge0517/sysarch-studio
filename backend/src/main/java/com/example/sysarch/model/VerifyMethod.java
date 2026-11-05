/**
 */
package com.example.sysarch.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.Enumerator;

/**
 * <!-- begin-user-doc -->
 * A representation of the literals of the enumeration '<em><b>Verify Method</b></em>',
 * and utility methods for working with them.
 * <!-- end-user-doc -->
 * @see com.example.sysarch.model.SysarchPackage#getVerifyMethod()
 * @model
 * @generated
 */
public enum VerifyMethod implements Enumerator {
	/**
	 * The '<em><b>ANALYSIS</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #ANALYSIS_VALUE
	 * @generated
	 * @ordered
	 */
	ANALYSIS(0, "ANALYSIS", "ANALYSIS"),

	/**
	 * The '<em><b>INSPECTION</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #INSPECTION_VALUE
	 * @generated
	 * @ordered
	 */
	INSPECTION(1, "INSPECTION", "INSPECTION"),

	/**
	 * The '<em><b>TEST</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #TEST_VALUE
	 * @generated
	 * @ordered
	 */
	TEST(2, "TEST", "TEST"),

	/**
	 * The '<em><b>DEMONSTRATION</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #DEMONSTRATION_VALUE
	 * @generated
	 * @ordered
	 */
	DEMONSTRATION(3, "DEMONSTRATION", "DEMONSTRATION");

	/**
	 * The '<em><b>ANALYSIS</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #ANALYSIS
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int ANALYSIS_VALUE = 0;

	/**
	 * The '<em><b>INSPECTION</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #INSPECTION
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int INSPECTION_VALUE = 1;

	/**
	 * The '<em><b>TEST</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #TEST
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int TEST_VALUE = 2;

	/**
	 * The '<em><b>DEMONSTRATION</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #DEMONSTRATION
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int DEMONSTRATION_VALUE = 3;

	/**
	 * An array of all the '<em><b>Verify Method</b></em>' enumerators.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private static final VerifyMethod[] VALUES_ARRAY =
		new VerifyMethod[] {
			ANALYSIS,
			INSPECTION,
			TEST,
			DEMONSTRATION,
		};

	/**
	 * A public read-only list of all the '<em><b>Verify Method</b></em>' enumerators.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public static final List<VerifyMethod> VALUES = Collections.unmodifiableList(Arrays.asList(VALUES_ARRAY));

	/**
	 * Returns the '<em><b>Verify Method</b></em>' literal with the specified literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param literal the literal.
	 * @return the matching enumerator or <code>null</code>.
	 * @generated
	 */
	public static VerifyMethod get(String literal) {
		for (int i = 0; i < VALUES_ARRAY.length; ++i) {
			VerifyMethod result = VALUES_ARRAY[i];
			if (result.toString().equals(literal)) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Returns the '<em><b>Verify Method</b></em>' literal with the specified name.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param name the name.
	 * @return the matching enumerator or <code>null</code>.
	 * @generated
	 */
	public static VerifyMethod getByName(String name) {
		for (int i = 0; i < VALUES_ARRAY.length; ++i) {
			VerifyMethod result = VALUES_ARRAY[i];
			if (result.getName().equals(name)) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Returns the '<em><b>Verify Method</b></em>' literal with the specified integer value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the integer value.
	 * @return the matching enumerator or <code>null</code>.
	 * @generated
	 */
	public static VerifyMethod get(int value) {
		switch (value) {
			case ANALYSIS_VALUE: return ANALYSIS;
			case INSPECTION_VALUE: return INSPECTION;
			case TEST_VALUE: return TEST;
			case DEMONSTRATION_VALUE: return DEMONSTRATION;
		}
		return null;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private final int value;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private final String name;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private final String literal;

	/**
	 * Only this class can construct instances.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private VerifyMethod(int value, String name, String literal) {
		this.value = value;
		this.name = name;
		this.literal = literal;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getValue() {
	  return value;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getName() {
	  return name;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getLiteral() {
	  return literal;
	}

	/**
	 * Returns the literal value of the enumerator, which is its string representation.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String toString() {
		return literal;
	}
	
} //VerifyMethod
