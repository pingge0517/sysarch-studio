/**
 */
package com.example.sysarch.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.Enumerator;

/**
 * <!-- begin-user-doc -->
 * A representation of the literals of the enumeration '<em><b>Layer</b></em>',
 * and utility methods for working with them.
 * <!-- end-user-doc -->
 * @see com.example.sysarch.model.SysarchPackage#getLayer()
 * @model
 * @generated
 */
public enum Layer implements Enumerator {
	/**
	 * The '<em><b>OPERATIONAL</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #OPERATIONAL_VALUE
	 * @generated
	 * @ordered
	 */
	OPERATIONAL(0, "OPERATIONAL", "OPERATIONAL"),

	/**
	 * The '<em><b>SYSTEM</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #SYSTEM_VALUE
	 * @generated
	 * @ordered
	 */
	SYSTEM(1, "SYSTEM", "SYSTEM"),

	/**
	 * The '<em><b>LOGICAL</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #LOGICAL_VALUE
	 * @generated
	 * @ordered
	 */
	LOGICAL(2, "LOGICAL", "LOGICAL"),

	/**
	 * The '<em><b>PHYSICAL</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #PHYSICAL_VALUE
	 * @generated
	 * @ordered
	 */
	PHYSICAL(3, "PHYSICAL", "PHYSICAL"),

	/**
	 * The '<em><b>EPBS</b></em>' literal object.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #EPBS_VALUE
	 * @generated
	 * @ordered
	 */
	EPBS(4, "EPBS", "EPBS");

	/**
	 * The '<em><b>OPERATIONAL</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #OPERATIONAL
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int OPERATIONAL_VALUE = 0;

	/**
	 * The '<em><b>SYSTEM</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #SYSTEM
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int SYSTEM_VALUE = 1;

	/**
	 * The '<em><b>LOGICAL</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #LOGICAL
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int LOGICAL_VALUE = 2;

	/**
	 * The '<em><b>PHYSICAL</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #PHYSICAL
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int PHYSICAL_VALUE = 3;

	/**
	 * The '<em><b>EPBS</b></em>' literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #EPBS
	 * @model
	 * @generated
	 * @ordered
	 */
	public static final int EPBS_VALUE = 4;

	/**
	 * An array of all the '<em><b>Layer</b></em>' enumerators.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private static final Layer[] VALUES_ARRAY =
		new Layer[] {
			OPERATIONAL,
			SYSTEM,
			LOGICAL,
			PHYSICAL,
			EPBS,
		};

	/**
	 * A public read-only list of all the '<em><b>Layer</b></em>' enumerators.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public static final List<Layer> VALUES = Collections.unmodifiableList(Arrays.asList(VALUES_ARRAY));

	/**
	 * Returns the '<em><b>Layer</b></em>' literal with the specified literal value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param literal the literal.
	 * @return the matching enumerator or <code>null</code>.
	 * @generated
	 */
	public static Layer get(String literal) {
		for (int i = 0; i < VALUES_ARRAY.length; ++i) {
			Layer result = VALUES_ARRAY[i];
			if (result.toString().equals(literal)) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Returns the '<em><b>Layer</b></em>' literal with the specified name.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param name the name.
	 * @return the matching enumerator or <code>null</code>.
	 * @generated
	 */
	public static Layer getByName(String name) {
		for (int i = 0; i < VALUES_ARRAY.length; ++i) {
			Layer result = VALUES_ARRAY[i];
			if (result.getName().equals(name)) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Returns the '<em><b>Layer</b></em>' literal with the specified integer value.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the integer value.
	 * @return the matching enumerator or <code>null</code>.
	 * @generated
	 */
	public static Layer get(int value) {
		switch (value) {
			case OPERATIONAL_VALUE: return OPERATIONAL;
			case SYSTEM_VALUE: return SYSTEM;
			case LOGICAL_VALUE: return LOGICAL;
			case PHYSICAL_VALUE: return PHYSICAL;
			case EPBS_VALUE: return EPBS;
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
	private Layer(int value, String name, String literal) {
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
	
} //Layer
