/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Message</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Message#getKind <em>Kind</em>}</li>
 *   <li>{@link com.example.sysarch.model.Message#getSender <em>Sender</em>}</li>
 *   <li>{@link com.example.sysarch.model.Message#getReceiver <em>Receiver</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getMessage()
 * @model
 * @generated
 */
public interface Message extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Kind</b></em>' attribute.
	 * The default value is <code>"SYNC"</code>.
	 * The literals are from the enumeration {@link com.example.sysarch.model.MessageKind}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Kind</em>' attribute.
	 * @see com.example.sysarch.model.MessageKind
	 * @see #setKind(MessageKind)
	 * @see com.example.sysarch.model.SysarchPackage#getMessage_Kind()
	 * @model default="SYNC"
	 * @generated
	 */
	MessageKind getKind();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Message#getKind <em>Kind</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Kind</em>' attribute.
	 * @see com.example.sysarch.model.MessageKind
	 * @see #getKind()
	 * @generated
	 */
	void setKind(MessageKind value);

	/**
	 * Returns the value of the '<em><b>Sender</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Sender</em>' reference.
	 * @see #setSender(Lifeline)
	 * @see com.example.sysarch.model.SysarchPackage#getMessage_Sender()
	 * @model required="true"
	 * @generated
	 */
	Lifeline getSender();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Message#getSender <em>Sender</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Sender</em>' reference.
	 * @see #getSender()
	 * @generated
	 */
	void setSender(Lifeline value);

	/**
	 * Returns the value of the '<em><b>Receiver</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Receiver</em>' reference.
	 * @see #setReceiver(Lifeline)
	 * @see com.example.sysarch.model.SysarchPackage#getMessage_Receiver()
	 * @model required="true"
	 * @generated
	 */
	Lifeline getReceiver();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Message#getReceiver <em>Receiver</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Receiver</em>' reference.
	 * @see #getReceiver()
	 * @generated
	 */
	void setReceiver(Lifeline value);

} // Message
