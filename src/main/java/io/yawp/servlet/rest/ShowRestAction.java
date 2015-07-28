package io.yawp.servlet.rest;

import io.yawp.repository.query.DatastoreQuery;

public class ShowRestAction extends RestAction {

	public ShowRestAction() {
		super("show");
	}

	@Override
	public void shield() {
		shield.protectShow();
	}

	@Override
	public Object action() {
		DatastoreQuery<?> query = query();

		if (hasTransformer()) {
			Object object = query.transform(getTransformerName()).fetch(id);
			applyGetFacade(object);
			return object;
		}

		if (hasShieldCondition()) {
			query.and(shield.getCondition());
		}

		Object object = query.fetch(id);
		applyGetFacade(object);
		return object;
	}

}
