/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Parameter</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Parameter#getUnit <em>Unit</em>}</li>
 *   <li>{@link com.example.sysarch.model.Parameter#getDirection <em>Direction</em>}</li>
 *   <li>{@link com.example.sysarch.model.Parameter#getDefaultValue <em>Default Value</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getParameter()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot DirectionKnown='direction.oclIsUndefined() or direction = \'IN\' or direction = \'OUT\' or direction = \'INOUT\''"
 * @generated
 */
public interface Parameter extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Unit</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Unit</em>' attribute.
	 * @see #setUnit(String)
	 * @see com.example.sysarch.model.SysarchPackage#getParameter_Unit()
	 * @model
	 * @generated
	 */
	String getUnit();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Parameter#getUnit <em>Unit</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Unit</em>' attribute.
	 * @see #getUnit()
	 * @generated
	 */
	void setUnit(String value);

	/**
	 * Returns the value of the '<em><b>Direction</b></em>' attribute.
	 * The default value is <code>"IN"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Direction</em>' attribute.
	 * @see #setDirection(String)
	 * @see com.example.sysarch.model.SysarchPackage#getParameter_Direction()
	 * @model default="IN"
	 * @generated
	 */
	String getDirection();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Parameter#getDirection <em>Direction</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Direction</em>' attribute.
	 * @see #getDirection()
	 * @generated
	 */
	void setDirection(String value);

	/**
	 * Returns the value of the '<em><b>Default Value</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Default Value</em>' attribute.
	 * @see #setDefaultValue(double)
	 * @see com.example.sysarch.model.SysarchPackage#getParameter_DefaultValue()
	 * @model
	 * @generated
	 */
	double getDefaultValue();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Parameter#getDefaultValue <em>Default Value</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Default Value</em>' attribute.
	 * @see #getDefaultValue()
	 * @generated
	 */
	void setDefaultValue(double value);

} // Parameter
