/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Requirement</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Requirement#getReqId <em>Req Id</em>}</li>
 *   <li>{@link com.example.sysarch.model.Requirement#getText <em>Text</em>}</li>
 *   <li>{@link com.example.sysarch.model.Requirement#getVerifyMethod <em>Verify Method</em>}</li>
 *   <li>{@link com.example.sysarch.model.Requirement#getPriority <em>Priority</em>}</li>
 *   <li>{@link com.example.sysarch.model.Requirement#getSatisfiedBy <em>Satisfied By</em>}</li>
 *   <li>{@link com.example.sysarch.model.Requirement#getVerifiedBy <em>Verified By</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getRequirement()
 * @model
 * @generated
 */
public interface Requirement extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Req Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Req Id</em>' attribute.
	 * @see #setReqId(String)
	 * @see com.example.sysarch.model.SysarchPackage#getRequirement_ReqId()
	 * @model
	 * @generated
	 */
	String getReqId();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Requirement#getReqId <em>Req Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Req Id</em>' attribute.
	 * @see #getReqId()
	 * @generated
	 */
	void setReqId(String value);

	/**
	 * Returns the value of the '<em><b>Text</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Text</em>' attribute.
	 * @see #setText(String)
	 * @see com.example.sysarch.model.SysarchPackage#getRequirement_Text()
	 * @model
	 * @generated
	 */
	String getText();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Requirement#getText <em>Text</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Text</em>' attribute.
	 * @see #getText()
	 * @generated
	 */
	void setText(String value);

	/**
	 * Returns the value of the '<em><b>Verify Method</b></em>' attribute.
	 * The default value is <code>"TEST"</code>.
	 * The literals are from the enumeration {@link com.example.sysarch.model.VerifyMethod}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Verify Method</em>' attribute.
	 * @see com.example.sysarch.model.VerifyMethod
	 * @see #setVerifyMethod(VerifyMethod)
	 * @see com.example.sysarch.model.SysarchPackage#getRequirement_VerifyMethod()
	 * @model default="TEST"
	 * @generated
	 */
	VerifyMethod getVerifyMethod();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Requirement#getVerifyMethod <em>Verify Method</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Verify Method</em>' attribute.
	 * @see com.example.sysarch.model.VerifyMethod
	 * @see #getVerifyMethod()
	 * @generated
	 */
	void setVerifyMethod(VerifyMethod value);

	/**
	 * Returns the value of the '<em><b>Priority</b></em>' attribute.
	 * The default value is <code>"3"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Priority</em>' attribute.
	 * @see #setPriority(int)
	 * @see com.example.sysarch.model.SysarchPackage#getRequirement_Priority()
	 * @model default="3"
	 * @generated
	 */
	int getPriority();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Requirement#getPriority <em>Priority</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Priority</em>' attribute.
	 * @see #getPriority()
	 * @generated
	 */
	void setPriority(int value);

	/**
	 * Returns the value of the '<em><b>Satisfied By</b></em>' reference list.
	 * The list contents are of type {@link com.example.sysarch.model.ArchElement}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Satisfied By</em>' reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getRequirement_SatisfiedBy()
	 * @model
	 * @generated
	 */
	EList<ArchElement> getSatisfiedBy();

	/**
	 * Returns the value of the '<em><b>Verified By</b></em>' reference list.
	 * The list contents are of type {@link com.example.sysarch.model.ArchElement}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Verified By</em>' reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getRequirement_VerifiedBy()
	 * @model
	 * @generated
	 */
	EList<ArchElement> getVerifiedBy();

} // Requirement
