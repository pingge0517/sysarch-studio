/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.VerifyMethod;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.ecore.EClass;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Requirement</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.RequirementImpl#getReqId <em>Req Id</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.RequirementImpl#getText <em>Text</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.RequirementImpl#getVerifyMethod <em>Verify Method</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.RequirementImpl#getPriority <em>Priority</em>}</li>
 * </ul>
 *
 * @generated
 */
public class RequirementImpl extends ArchElementImpl implements Requirement {
	/**
	 * The default value of the '{@link #getReqId() <em>Req Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getReqId()
	 * @generated
	 * @ordered
	 */
	protected static final String REQ_ID_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getReqId() <em>Req Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getReqId()
	 * @generated
	 * @ordered
	 */
	protected String reqId = REQ_ID_EDEFAULT;

	/**
	 * The default value of the '{@link #getText() <em>Text</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getText()
	 * @generated
	 * @ordered
	 */
	protected static final String TEXT_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getText() <em>Text</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getText()
	 * @generated
	 * @ordered
	 */
	protected String text = TEXT_EDEFAULT;

	/**
	 * The default value of the '{@link #getVerifyMethod() <em>Verify Method</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVerifyMethod()
	 * @generated
	 * @ordered
	 */
	protected static final VerifyMethod VERIFY_METHOD_EDEFAULT = VerifyMethod.TEST;

	/**
	 * The cached value of the '{@link #getVerifyMethod() <em>Verify Method</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVerifyMethod()
	 * @generated
	 * @ordered
	 */
	protected VerifyMethod verifyMethod = VERIFY_METHOD_EDEFAULT;

	/**
	 * The default value of the '{@link #getPriority() <em>Priority</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPriority()
	 * @generated
	 * @ordered
	 */
	protected static final int PRIORITY_EDEFAULT = 3;

	/**
	 * The cached value of the '{@link #getPriority() <em>Priority</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPriority()
	 * @generated
	 * @ordered
	 */
	protected int priority = PRIORITY_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected RequirementImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.REQUIREMENT;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getReqId() {
		return reqId;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setReqId(String newReqId) {
		String oldReqId = reqId;
		reqId = newReqId;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.REQUIREMENT__REQ_ID, oldReqId, reqId));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getText() {
		return text;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setText(String newText) {
		String oldText = text;
		text = newText;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.REQUIREMENT__TEXT, oldText, text));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public VerifyMethod getVerifyMethod() {
		return verifyMethod;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setVerifyMethod(VerifyMethod newVerifyMethod) {
		VerifyMethod oldVerifyMethod = verifyMethod;
		verifyMethod = newVerifyMethod == null ? VERIFY_METHOD_EDEFAULT : newVerifyMethod;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.REQUIREMENT__VERIFY_METHOD, oldVerifyMethod, verifyMethod));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getPriority() {
		return priority;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setPriority(int newPriority) {
		int oldPriority = priority;
		priority = newPriority;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.REQUIREMENT__PRIORITY, oldPriority, priority));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.REQUIREMENT__REQ_ID:
				return getReqId();
			case SysarchPackage.REQUIREMENT__TEXT:
				return getText();
			case SysarchPackage.REQUIREMENT__VERIFY_METHOD:
				return getVerifyMethod();
			case SysarchPackage.REQUIREMENT__PRIORITY:
				return getPriority();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case SysarchPackage.REQUIREMENT__REQ_ID:
				setReqId((String)newValue);
				return;
			case SysarchPackage.REQUIREMENT__TEXT:
				setText((String)newValue);
				return;
			case SysarchPackage.REQUIREMENT__VERIFY_METHOD:
				setVerifyMethod((VerifyMethod)newValue);
				return;
			case SysarchPackage.REQUIREMENT__PRIORITY:
				setPriority((Integer)newValue);
				return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
			case SysarchPackage.REQUIREMENT__REQ_ID:
				setReqId(REQ_ID_EDEFAULT);
				return;
			case SysarchPackage.REQUIREMENT__TEXT:
				setText(TEXT_EDEFAULT);
				return;
			case SysarchPackage.REQUIREMENT__VERIFY_METHOD:
				setVerifyMethod(VERIFY_METHOD_EDEFAULT);
				return;
			case SysarchPackage.REQUIREMENT__PRIORITY:
				setPriority(PRIORITY_EDEFAULT);
				return;
		}
		super.eUnset(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
			case SysarchPackage.REQUIREMENT__REQ_ID:
				return REQ_ID_EDEFAULT == null ? reqId != null : !REQ_ID_EDEFAULT.equals(reqId);
			case SysarchPackage.REQUIREMENT__TEXT:
				return TEXT_EDEFAULT == null ? text != null : !TEXT_EDEFAULT.equals(text);
			case SysarchPackage.REQUIREMENT__VERIFY_METHOD:
				return verifyMethod != VERIFY_METHOD_EDEFAULT;
			case SysarchPackage.REQUIREMENT__PRIORITY:
				return priority != PRIORITY_EDEFAULT;
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (reqId: ");
		result.append(reqId);
		result.append(", text: ");
		result.append(text);
		result.append(", verifyMethod: ");
		result.append(verifyMethod);
		result.append(", priority: ");
		result.append(priority);
		result.append(')');
		return result.toString();
	}

} //RequirementImpl
