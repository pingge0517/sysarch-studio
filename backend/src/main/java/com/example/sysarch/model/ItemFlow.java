/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Item Flow</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.ItemFlow#getConnection <em>Connection</em>}</li>
 *   <li>{@link com.example.sysarch.model.ItemFlow#getItemName <em>Item Name</em>}</li>
 *   <li>{@link com.example.sysarch.model.ItemFlow#getItemType <em>Item Type</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getItemFlow()
 * @model
 * @generated
 */
public interface ItemFlow extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Connection</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Connection</em>' reference.
	 * @see #setConnection(Connection)
	 * @see com.example.sysarch.model.SysarchPackage#getItemFlow_Connection()
	 * @model
	 * @generated
	 */
	Connection getConnection();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ItemFlow#getConnection <em>Connection</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Connection</em>' reference.
	 * @see #getConnection()
	 * @generated
	 */
	void setConnection(Connection value);

	/**
	 * Returns the value of the '<em><b>Item Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Item Name</em>' attribute.
	 * @see #setItemName(String)
	 * @see com.example.sysarch.model.SysarchPackage#getItemFlow_ItemName()
	 * @model
	 * @generated
	 */
	String getItemName();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ItemFlow#getItemName <em>Item Name</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Item Name</em>' attribute.
	 * @see #getItemName()
	 * @generated
	 */
	void setItemName(String value);

	/**
	 * Returns the value of the '<em><b>Item Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Item Type</em>' attribute.
	 * @see #setItemType(String)
	 * @see com.example.sysarch.model.SysarchPackage#getItemFlow_ItemType()
	 * @model
	 * @generated
	 */
	String getItemType();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ItemFlow#getItemType <em>Item Type</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Item Type</em>' attribute.
	 * @see #getItemType()
	 * @generated
	 */
	void setItemType(String value);

} // ItemFlow
