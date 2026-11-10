/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.State;
import com.example.sysarch.model.SysarchPackage;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.ecore.EClass;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>State</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.StateImpl#isIsInitial <em>Is Initial</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.StateImpl#isIsFinal <em>Is Final</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.StateImpl#getDoBehavior <em>Do Behavior</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.StateImpl#getEntryBehavior <em>Entry Behavior</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.StateImpl#getExitBehavior <em>Exit Behavior</em>}</li>
 * </ul>
 *
 * @generated
 */
public class StateImpl extends ArchElementImpl implements State {
	/**
	 * The default value of the '{@link #isIsInitial() <em>Is Initial</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsInitial()
	 * @generated
	 * @ordered
	 */
	protected static final boolean IS_INITIAL_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isIsInitial() <em>Is Initial</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsInitial()
	 * @generated
	 * @ordered
	 */
	protected boolean isInitial = IS_INITIAL_EDEFAULT;

	/**
	 * The default value of the '{@link #isIsFinal() <em>Is Final</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsFinal()
	 * @generated
	 * @ordered
	 */
	protected static final boolean IS_FINAL_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isIsFinal() <em>Is Final</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsFinal()
	 * @generated
	 * @ordered
	 */
	protected boolean isFinal = IS_FINAL_EDEFAULT;

	/**
	 * The default value of the '{@link #getDoBehavior() <em>Do Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDoBehavior()
	 * @generated
	 * @ordered
	 */
	protected static final String DO_BEHAVIOR_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getDoBehavior() <em>Do Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDoBehavior()
	 * @generated
	 * @ordered
	 */
	protected String doBehavior = DO_BEHAVIOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getEntryBehavior() <em>Entry Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEntryBehavior()
	 * @generated
	 * @ordered
	 */
	protected static final String ENTRY_BEHAVIOR_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getEntryBehavior() <em>Entry Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEntryBehavior()
	 * @generated
	 * @ordered
	 */
	protected String entryBehavior = ENTRY_BEHAVIOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getExitBehavior() <em>Exit Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getExitBehavior()
	 * @generated
	 * @ordered
	 */
	protected static final String EXIT_BEHAVIOR_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getExitBehavior() <em>Exit Behavior</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getExitBehavior()
	 * @generated
	 * @ordered
	 */
	protected String exitBehavior = EXIT_BEHAVIOR_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected StateImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.STATE;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean isIsInitial() {
		return isInitial;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setIsInitial(boolean newIsInitial) {
		boolean oldIsInitial = isInitial;
		isInitial = newIsInitial;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.STATE__IS_INITIAL, oldIsInitial, isInitial));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean isIsFinal() {
		return isFinal;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setIsFinal(boolean newIsFinal) {
		boolean oldIsFinal = isFinal;
		isFinal = newIsFinal;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.STATE__IS_FINAL, oldIsFinal, isFinal));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getDoBehavior() {
		return doBehavior;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setDoBehavior(String newDoBehavior) {
		String oldDoBehavior = doBehavior;
		doBehavior = newDoBehavior;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.STATE__DO_BEHAVIOR, oldDoBehavior, doBehavior));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getEntryBehavior() {
		return entryBehavior;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setEntryBehavior(String newEntryBehavior) {
		String oldEntryBehavior = entryBehavior;
		entryBehavior = newEntryBehavior;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.STATE__ENTRY_BEHAVIOR, oldEntryBehavior, entryBehavior));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getExitBehavior() {
		return exitBehavior;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setExitBehavior(String newExitBehavior) {
		String oldExitBehavior = exitBehavior;
		exitBehavior = newExitBehavior;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.STATE__EXIT_BEHAVIOR, oldExitBehavior, exitBehavior));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.STATE__IS_INITIAL:
				return isIsInitial();
			case SysarchPackage.STATE__IS_FINAL:
				return isIsFinal();
			case SysarchPackage.STATE__DO_BEHAVIOR:
				return getDoBehavior();
			case SysarchPackage.STATE__ENTRY_BEHAVIOR:
				return getEntryBehavior();
			case SysarchPackage.STATE__EXIT_BEHAVIOR:
				return getExitBehavior();
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
			case SysarchPackage.STATE__IS_INITIAL:
				setIsInitial((Boolean)newValue);
				return;
			case SysarchPackage.STATE__IS_FINAL:
				setIsFinal((Boolean)newValue);
				return;
			case SysarchPackage.STATE__DO_BEHAVIOR:
				setDoBehavior((String)newValue);
				return;
			case SysarchPackage.STATE__ENTRY_BEHAVIOR:
				setEntryBehavior((String)newValue);
				return;
			case SysarchPackage.STATE__EXIT_BEHAVIOR:
				setExitBehavior((String)newValue);
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
			case SysarchPackage.STATE__IS_INITIAL:
				setIsInitial(IS_INITIAL_EDEFAULT);
				return;
			case SysarchPackage.STATE__IS_FINAL:
				setIsFinal(IS_FINAL_EDEFAULT);
				return;
			case SysarchPackage.STATE__DO_BEHAVIOR:
				setDoBehavior(DO_BEHAVIOR_EDEFAULT);
				return;
			case SysarchPackage.STATE__ENTRY_BEHAVIOR:
				setEntryBehavior(ENTRY_BEHAVIOR_EDEFAULT);
				return;
			case SysarchPackage.STATE__EXIT_BEHAVIOR:
				setExitBehavior(EXIT_BEHAVIOR_EDEFAULT);
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
			case SysarchPackage.STATE__IS_INITIAL:
				return isInitial != IS_INITIAL_EDEFAULT;
			case SysarchPackage.STATE__IS_FINAL:
				return isFinal != IS_FINAL_EDEFAULT;
			case SysarchPackage.STATE__DO_BEHAVIOR:
				return DO_BEHAVIOR_EDEFAULT == null ? doBehavior != null : !DO_BEHAVIOR_EDEFAULT.equals(doBehavior);
			case SysarchPackage.STATE__ENTRY_BEHAVIOR:
				return ENTRY_BEHAVIOR_EDEFAULT == null ? entryBehavior != null : !ENTRY_BEHAVIOR_EDEFAULT.equals(entryBehavior);
			case SysarchPackage.STATE__EXIT_BEHAVIOR:
				return EXIT_BEHAVIOR_EDEFAULT == null ? exitBehavior != null : !EXIT_BEHAVIOR_EDEFAULT.equals(exitBehavior);
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
		result.append(" (isInitial: ");
		result.append(isInitial);
		result.append(", isFinal: ");
		result.append(isFinal);
		result.append(", doBehavior: ");
		result.append(doBehavior);
		result.append(", entryBehavior: ");
		result.append(entryBehavior);
		result.append(", exitBehavior: ");
		result.append(exitBehavior);
		result.append(')');
		return result.toString();
	}

} //StateImpl
