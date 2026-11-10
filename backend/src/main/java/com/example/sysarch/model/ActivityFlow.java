/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Activity Flow</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.ActivityFlow#getSource <em>Source</em>}</li>
 *   <li>{@link com.example.sysarch.model.ActivityFlow#getTarget <em>Target</em>}</li>
 *   <li>{@link com.example.sysarch.model.ActivityFlow#isIsObjectFlow <em>Is Object Flow</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getActivityFlow()
 * @model
 * @generated
 */
public interface ActivityFlow extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Source</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Source</em>' reference.
	 * @see #setSource(Action)
	 * @see com.example.sysarch.model.SysarchPackage#getActivityFlow_Source()
	 * @model required="true"
	 * @generated
	 */
	Action getSource();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ActivityFlow#getSource <em>Source</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Source</em>' reference.
	 * @see #getSource()
	 * @generated
	 */
	void setSource(Action value);

	/**
	 * Returns the value of the '<em><b>Target</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Target</em>' reference.
	 * @see #setTarget(Action)
	 * @see com.example.sysarch.model.SysarchPackage#getActivityFlow_Target()
	 * @model required="true"
	 * @generated
	 */
	Action getTarget();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ActivityFlow#getTarget <em>Target</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Target</em>' reference.
	 * @see #getTarget()
	 * @generated
	 */
	void setTarget(Action value);

	/**
	 * Returns the value of the '<em><b>Is Object Flow</b></em>' attribute.
	 * The default value is <code>"false"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Is Object Flow</em>' attribute.
	 * @see #setIsObjectFlow(boolean)
	 * @see com.example.sysarch.model.SysarchPackage#getActivityFlow_IsObjectFlow()
	 * @model default="false"
	 * @generated
	 */
	boolean isIsObjectFlow();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ActivityFlow#isIsObjectFlow <em>Is Object Flow</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Is Object Flow</em>' attribute.
	 * @see #isIsObjectFlow()
	 * @generated
	 */
	void setIsObjectFlow(boolean value);

} // ActivityFlow
