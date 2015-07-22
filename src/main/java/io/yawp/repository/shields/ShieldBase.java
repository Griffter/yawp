package io.yawp.repository.shields;

import io.yawp.repository.Feature;
import io.yawp.repository.IdRef;
import io.yawp.repository.actions.ActionKey;
import io.yawp.repository.query.condition.BaseCondition;
import io.yawp.servlet.HttpException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public abstract class ShieldBase<T> extends Feature {

	private boolean allow = false;

	private boolean lastAllow = false;

	private ShieldConditions conditions;

	private Class<?> endpointClazz;

	private IdRef<?> id;

	private List<T> objects;

	private Map<String, String> params;

	private ActionKey actionKey;

	private Map<ActionKey, Method> actionMethods;

	public abstract void always();

	public abstract void index(IdRef<?> parentId);

	public abstract void show(IdRef<T> id);

	public abstract void create(List<T> objects);

	public abstract void update(IdRef<T> id, T object);

	public abstract void destroy(IdRef<T> id);

	public abstract void custom();

	protected ShieldBase<T> allow() {
		return allow(true);
	}

	protected final ShieldBase<T> allow(boolean allow) {
		this.allow = this.allow || allow;
		this.lastAllow = allow;
		return this;
	}

	protected final ShieldBase<T> where(BaseCondition condition) {
		if (!lastAllow) {
			return this;
		}

		getConditions().where(condition);
		return this;
	}

	protected final ShieldBase<T> whereParent(BaseCondition condition) {
		if (!lastAllow) {
			return this;
		}

		getConditions().whereParent(condition);
		return this;
	}

	protected final ShieldBase<T> whereGrandparent(BaseCondition condition) {
		if (!lastAllow) {
			return this;
		}

		getConditions().whereGrandparent(condition);
		return this;
	}

	protected final ShieldBase<T> whereAncestor(int ancestorIndex, BaseCondition condition) {
		if (!lastAllow) {
			return this;
		}

		getConditions().whereAncestor(ancestorIndex, condition);
		return this;
	}

	protected final boolean requestHasAnyObject() {
		return objects != null;
	}

	public final void protectIndex() {
		always();
		index(id);
		throwNotFoundIfNotAllowed();
	}

	@SuppressWarnings("unchecked")
	public final void protectShow() {
		always();
		show((IdRef<T>) id);
		throwNotFoundIfNotAllowed();
	}

	public final void protectCreate() {
		always();
		create(objects);
		throwNotFoundIfNotAllowed();

		verifyConditions();
		throwForbiddenIfNotAllowed();
	}

	@SuppressWarnings("unchecked")
	public final void protectUpdate() {
		always();
		update((IdRef<T>) id, objects == null ? null : objects.get(0));
		throwNotFoundIfNotAllowed();

		verifyConditions();
		throwForbiddenIfNotAllowed();
	}

	@SuppressWarnings("unchecked")
	public final void protectDestroy() {
		always();
		destroy((IdRef<T>) id);
		throwNotFoundIfNotAllowed();

		verifyConditions();
		throwForbiddenIfNotAllowed();
	}

	public final void protectCustom() {
		always();
		custom();
		annotadedCustoms();
		throwNotFoundIfNotAllowed();

		verifyConditions();
		throwForbiddenIfNotAllowed();
	}

	private void throwNotFoundIfNotAllowed() {
		if (!allow) {
			throw new HttpException(404);
		}
	}

	private void throwForbiddenIfNotAllowed() {
		if (!allow) {
			throw new HttpException(403);
		}
	}

	public void setEndpointClazz(Class<?> endpointClazz) {
		this.endpointClazz = endpointClazz;
	}

	public final void setId(IdRef<?> id) {
		this.id = id;
	}

	@SuppressWarnings("unchecked")
	public final void setObjects(List<?> objects) {
		this.objects = (List<T>) objects;
	}

	public BaseCondition getCondition() {
		return conditions.getWhere();
	}

	public boolean hasCondition() {
		return getConditions().getWhere() != null;
	}

	private ShieldConditions getConditions() {
		if (conditions != null) {
			return conditions;
		}

		conditions = new ShieldConditions(endpointClazz, id, objects);
		return conditions;
	}

	private void verifyConditions() {
		this.allow = getConditions().evaluate();
	}

	public final void setParams(Map<String, String> params) {
		this.params = params;
	}

	public final void setActionKey(ActionKey actionKey) {
		this.actionKey = actionKey;
	}

	public final void setActionMethods(Map<ActionKey, Method> actionMethods) {
		this.actionMethods = actionMethods;
	}

	private void annotadedCustoms() {
		if (!actionMethods.containsKey(actionKey)) {
			return;
		}

		Method method = actionMethods.get(actionKey);
		invokeCustomActionShield(method);
	}

	private void invokeCustomActionShield(Method method) {
		try {
			Object[] arguments = ActionKey.getActionMethodParameters(method, id, params);
			method.invoke(this, arguments);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}