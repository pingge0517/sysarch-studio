/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>State</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.State#isIsInitial <em>Is Initial</em>}</li>
 *   <li>{@link com.example.sysarch.model.State#isIsFinal <em>Is Final</em>}</li>
 *   <li>{@link com.example.sysarch.model.State#getDoBehavior <em>Do Behavior</em>}</li>
 *   <li>{@link com.example.sysarch.model.State#getEntryBehavior <em>Entry Behavior</em>}</li>
 *   <li>{@link com.example.sysarch.model.State#getExitBehavior <em>Exit Behavior</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getState()
 * @model
 * @generated
 */
public interface State extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Is Initial</b></em>' attribute.
	 * The default value is <code>"false"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Is Initial</em>' attribute.
	 * @see #setIsInitial(boolean)
	 * @see com.example.sysarch.model.SysarchPackage#getState_IsInitial()
	 * @model default="false"
	 * @generated
	 */
	boolean isIsInitial();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.State#isIsInitial <em>Is Initial</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Is Initial</em>' attribute.
	 * @see #isIsInitial()
	 * @generated
	 */
	void setIsInitial(boolean value);

	/**
	 * Returns the value of the '<em><b>Is Final</b></em>' attribute.
	 * The default value is <code>"false"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Is Final</em>' attribute.
	 * @see #setIsFinal(boolean)
	 * @see com.example.sysarch.model.SysarchPackage#getState_IsFinal()
	 * @model default="false"
	 * @generated
	 */
	boolean isIsFinal();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.State#isIsFinal <em>Is Final</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Is Final</em>' attribute.
	 * @see #isIsFinal()
	 * @generated
	 */
	void setIsFinal(boolean value);

	/**
	 * Returns the value of the '<em><b>Do Behavior</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Do Behavior</em>' attribute.
	 * @see #setDoBehavior(String)
	 * @see com.example.sysarch.model.SysarchPackage#getState_DoBehavior()
	 * @model
	 * @generated
	 */
	String getDoBehavior();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.State#getDoBehavior <em>Do Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Do Behavior</em>' attribute.
	 * @see #getDoBehavior()
	 * @generated
	 */
	void setDoBehavior(String value);

	/**
	 * Returns the value of the '<em><b>Entry Behavior</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Entry Behavior</em>' attribute.
	 * @see #setEntryBehavior(String)
	 * @see com.example.sysarch.model.SysarchPackage#getState_EntryBehavior()
	 * @model
	 * @generated
	 */
	String getEntryBehavior();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.State#getEntryBehavior <em>Entry Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Entry Behavior</em>' attribute.
	 * @see #getEntryBehavior()
	 * @generated
	 */
	void setEntryBehavior(String value);

	/**
	 * Returns the value of the '<em><b>Exit Behavior</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Exit Behavior</em>' attribute.
	 * @see #setExitBehavior(String)
	 * @see com.example.sysarch.model.SysarchPackage#getState_ExitBehavior()
	 * @model
	 * @generated
	 */
	String getExitBehavior();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.State#getExitBehavior <em>Exit Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Exit Behavior</em>' attribute.
	 * @see #getExitBehavior()
	 * @generated
	 */
	void setExitBehavior(String value);

} // State
