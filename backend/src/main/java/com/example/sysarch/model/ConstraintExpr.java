/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Constraint Expr</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.ConstraintExpr#getLanguage <em>Language</em>}</li>
 *   <li>{@link com.example.sysarch.model.ConstraintExpr#getBody <em>Body</em>}</li>
 *   <li>{@link com.example.sysarch.model.ConstraintExpr#getDeclaredVars <em>Declared Vars</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getConstraintExpr()
 * @model
 * @generated
 */
public interface ConstraintExpr extends EObject {
	/**
	 * Returns the value of the '<em><b>Language</b></em>' attribute.
	 * The default value is <code>"PYTHON"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Language</em>' attribute.
	 * @see #setLanguage(String)
	 * @see com.example.sysarch.model.SysarchPackage#getConstraintExpr_Language()
	 * @model default="PYTHON"
	 * @generated
	 */
	String getLanguage();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ConstraintExpr#getLanguage <em>Language</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Language</em>' attribute.
	 * @see #getLanguage()
	 * @generated
	 */
	void setLanguage(String value);

	/**
	 * Returns the value of the '<em><b>Body</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Body</em>' attribute.
	 * @see #setBody(String)
	 * @see com.example.sysarch.model.SysarchPackage#getConstraintExpr_Body()
	 * @model
	 * @generated
	 */
	String getBody();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ConstraintExpr#getBody <em>Body</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Body</em>' attribute.
	 * @see #getBody()
	 * @generated
	 */
	void setBody(String value);

	/**
	 * Returns the value of the '<em><b>Declared Vars</b></em>' attribute list.
	 * The list contents are of type {@link java.lang.String}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Declared Vars</em>' attribute list.
	 * @see com.example.sysarch.model.SysarchPackage#getConstraintExpr_DeclaredVars()
	 * @model
	 * @generated
	 */
	EList<String> getDeclaredVars();

} // ConstraintExpr
