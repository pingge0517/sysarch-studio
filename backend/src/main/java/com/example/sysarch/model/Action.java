/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Action</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Action#getKind <em>Kind</em>}</li>
 *   <li>{@link com.example.sysarch.model.Action#getExpression <em>Expression</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getAction()
 * @model
 * @generated
 */
public interface Action extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Kind</b></em>' attribute.
	 * The default value is <code>"CALL"</code>.
	 * The literals are from the enumeration {@link com.example.sysarch.model.ActionKind}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Kind</em>' attribute.
	 * @see com.example.sysarch.model.ActionKind
	 * @see #setKind(ActionKind)
	 * @see com.example.sysarch.model.SysarchPackage#getAction_Kind()
	 * @model default="CALL"
	 * @generated
	 */
	ActionKind getKind();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Action#getKind <em>Kind</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Kind</em>' attribute.
	 * @see com.example.sysarch.model.ActionKind
	 * @see #getKind()
	 * @generated
	 */
	void setKind(ActionKind value);

	/**
	 * Returns the value of the '<em><b>Expression</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Expression</em>' attribute.
	 * @see #setExpression(String)
	 * @see com.example.sysarch.model.SysarchPackage#getAction_Expression()
	 * @model
	 * @generated
	 */
	String getExpression();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Action#getExpression <em>Expression</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Expression</em>' attribute.
	 * @see #getExpression()
	 * @generated
	 */
	void setExpression(String value);

} // Action
