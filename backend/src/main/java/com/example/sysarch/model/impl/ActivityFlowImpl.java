/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.Action;
import com.example.sysarch.model.ActivityFlow;
import com.example.sysarch.model.SysarchPackage;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Activity Flow</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.ActivityFlowImpl#getSource <em>Source</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ActivityFlowImpl#getTarget <em>Target</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ActivityFlowImpl#isIsObjectFlow <em>Is Object Flow</em>}</li>
 * </ul>
 *
 * @generated
 */
public class ActivityFlowImpl extends ArchElementImpl implements ActivityFlow {
	/**
	 * The cached value of the '{@link #getSource() <em>Source</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSource()
	 * @generated
	 * @ordered
	 */
	protected Action source;

	/**
	 * The cached value of the '{@link #getTarget() <em>Target</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTarget()
	 * @generated
	 * @ordered
	 */
	protected Action target;

	/**
	 * The default value of the '{@link #isIsObjectFlow() <em>Is Object Flow</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsObjectFlow()
	 * @generated
	 * @ordered
	 */
	protected static final boolean IS_OBJECT_FLOW_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isIsObjectFlow() <em>Is Object Flow</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsObjectFlow()
	 * @generated
	 * @ordered
	 */
	protected boolean isObjectFlow = IS_OBJECT_FLOW_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected ActivityFlowImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.ACTIVITY_FLOW;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Action getSource() {
		if (source != null && source.eIsProxy()) {
			InternalEObject oldSource = (InternalEObject)source;
			source = (Action)eResolveProxy(oldSource);
			if (source != oldSource) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, SysarchPackage.ACTIVITY_FLOW__SOURCE, oldSource, source));
			}
		}
		return source;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Action basicGetSource() {
		return source;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setSource(Action newSource) {
		Action oldSource = source;
		source = newSource;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ACTIVITY_FLOW__SOURCE, oldSource, source));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Action getTarget() {
		if (target != null && target.eIsProxy()) {
			InternalEObject oldTarget = (InternalEObject)target;
			target = (Action)eResolveProxy(oldTarget);
			if (target != oldTarget) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, SysarchPackage.ACTIVITY_FLOW__TARGET, oldTarget, target));
			}
		}
		return target;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Action basicGetTarget() {
		return target;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setTarget(Action newTarget) {
		Action oldTarget = target;
		target = newTarget;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ACTIVITY_FLOW__TARGET, oldTarget, target));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean isIsObjectFlow() {
		return isObjectFlow;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setIsObjectFlow(boolean newIsObjectFlow) {
		boolean oldIsObjectFlow = isObjectFlow;
		isObjectFlow = newIsObjectFlow;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ACTIVITY_FLOW__IS_OBJECT_FLOW, oldIsObjectFlow, isObjectFlow));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.ACTIVITY_FLOW__SOURCE:
				if (resolve) return getSource();
				return basicGetSource();
			case SysarchPackage.ACTIVITY_FLOW__TARGET:
				if (resolve) return getTarget();
				return basicGetTarget();
			case SysarchPackage.ACTIVITY_FLOW__IS_OBJECT_FLOW:
				return isIsObjectFlow();
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
			case SysarchPackage.ACTIVITY_FLOW__SOURCE:
				setSource((Action)newValue);
				return;
			case SysarchPackage.ACTIVITY_FLOW__TARGET:
				setTarget((Action)newValue);
				return;
			case SysarchPackage.ACTIVITY_FLOW__IS_OBJECT_FLOW:
				setIsObjectFlow((Boolean)newValue);
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
			case SysarchPackage.ACTIVITY_FLOW__SOURCE:
				setSource((Action)null);
				return;
			case SysarchPackage.ACTIVITY_FLOW__TARGET:
				setTarget((Action)null);
				return;
			case SysarchPackage.ACTIVITY_FLOW__IS_OBJECT_FLOW:
				setIsObjectFlow(IS_OBJECT_FLOW_EDEFAULT);
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
			case SysarchPackage.ACTIVITY_FLOW__SOURCE:
				return source != null;
			case SysarchPackage.ACTIVITY_FLOW__TARGET:
				return target != null;
			case SysarchPackage.ACTIVITY_FLOW__IS_OBJECT_FLOW:
				return isObjectFlow != IS_OBJECT_FLOW_EDEFAULT;
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
		result.append(" (isObjectFlow: ");
		result.append(isObjectFlow);
		result.append(')');
		return result.toString();
	}

} //ActivityFlowImpl
