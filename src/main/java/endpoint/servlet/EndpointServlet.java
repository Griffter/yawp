package endpoint.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import endpoint.repository.EndpointException;
import endpoint.repository.EndpointFeatures;
import endpoint.repository.EndpointScanner;
import endpoint.repository.IdRef;
import endpoint.repository.Repository;
import endpoint.repository.RepositoryFeatures;
import endpoint.repository.actions.ActionKey;
import endpoint.repository.query.DatastoreQuery;
import endpoint.repository.query.DatastoreQueryOptions;
import endpoint.repository.query.NoResultException;
import endpoint.repository.response.ErrorResponse;
import endpoint.repository.response.HttpResponse;
import endpoint.repository.response.JsonResponse;
import endpoint.utils.EntityUtils;
import endpoint.utils.HttpVerb;
import endpoint.utils.JsonUtils;

public class EndpointServlet extends HttpServlet {

	private static final long serialVersionUID = 8155293897299089610L;

	private RepositoryFeatures features;

	private boolean enableHooks = true;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		setWithHooks(config.getInitParameter("enableHooks"));
		scanEndpoints(config.getInitParameter("packagePrefix"));
	}

	private void setWithHooks(String enableHooksParameter) {
		this.enableHooks = enableHooksParameter == null || Boolean.valueOf(enableHooksParameter);
	}

	public EndpointServlet() {
	}

	// TODO why protected? why not public? constructor vs init? tests?
	// inheritance?
	protected EndpointServlet(String packagePrefix) {
		scanEndpoints(packagePrefix);
	}

	private void scanEndpoints(String packagePrefix) {
		features = new EndpointScanner(packagePrefix).enableHooks(enableHooks).scan();
	}

	private void response(HttpServletResponse resp, HttpResponse httpResponse) throws IOException {
		if (httpResponse == null) {
			new JsonResponse("{\"status\":\"ok\"}").execute(resp);
		} else {
			httpResponse.execute(resp);
		}
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		HttpResponse httpResponse;
		try {
			httpResponse = execute(req.getMethod(), getUri(req), JsonUtils.readJson(req.getReader()), makeParams(req));
		} catch (HttpException e) {
			httpResponse = new ErrorResponse(e.getHttpStatus(), e.getText());
		} catch (EndpointException e) {
			httpResponse = new ErrorResponse(403);
		}

		response(resp, httpResponse);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> makeParams(HttpServletRequest req) {
		return req.getParameterMap();
	}

	private String getUri(HttpServletRequest req) {
		return req.getRequestURI().substring(req.getServletPath().length());
	}

	protected HttpResponse execute(String method, String uri, String requestJson, Map<String, String> params) {

		Repository r = getRepository(params);
		EndpointRouter router = EndpointRouter.parse(r, HttpVerb.fromString(method), uri);
		EndpointFeatures<?> endpoint = router.getEndpointFeatures();
		IdRef<?> idRef = router.getIdRef();

		switch (router.getRESTActionType()) {
		case INDEX:
			return new JsonResponse(index(r, idRef, endpoint, q(params), t(params)));
		case SHOW:
			try {
				return new JsonResponse(get(r, idRef, t(params)));
			} catch (NoResultException e) {
				throw new HttpException(404);
			}
		case CREATE:
			return new JsonResponse(save(r, idRef, endpoint, requestJson));
		case UPDATE:
			return new JsonResponse(save(r, idRef, endpoint, requestJson));
		case CUSTOM:
			return action(r, idRef, router.getEndpointClazz(), router.getCustomActionKey(), params);
		case DELETE:
			throw new HttpException(501, "DELETE is not implemented yet");
		default:
			throw new IllegalArgumentException("Invalid datastore action");
		}
	}

	private String q(Map<String, String> params) {
		return params == null ? null : params.get("q");
	}

	private String t(Map<String, String> params) {
		return params == null ? null : params.get("t");
	}

	protected Repository getRepository(Map<String, String> params) {
		return Repository.r().setFeatures(features);
	}

	private HttpResponse action(Repository r, IdRef<?> idRef, Class<?> clazz, ActionKey actionKey, Map<String, String> params) {
		return r.action(idRef, clazz, actionKey, params);
	}

	private String save(Repository r, IdRef<?> parentId, EndpointFeatures<?> endpoint, String json) {
		if (JsonUtils.isJsonArray(json)) {
			return saveFromArray(r, parentId, endpoint.getClazz(), json);
		} else {
			return saveFromObject(r, parentId, endpoint.getClazz(), json);
		}
	}

	private String saveFromObject(Repository r, IdRef<?> parentId, Class<?> clazz, String json) {
		Object object = JsonUtils.from(r, json, clazz);
		saveInRepository(r, object, parentId);
		return JsonUtils.to(object);
	}

	private String saveFromArray(Repository r, IdRef<?> parentId, Class<?> clazz, String json) {
		List<?> objects = JsonUtils.fromList(r, json, clazz);

		for (Object object : objects) {
			saveInRepository(r, object, parentId);
		}

		return JsonUtils.to(objects);
	}

	private String index(Repository r, IdRef<?> parentId, EndpointFeatures<?> endpoint, String q, String t) {
		DatastoreQuery<?> query = executeQueryInRepository(r, endpoint.getClazz());

		if (parentId != null) {
			query.from(parentId);
		}

		if (q != null) {
			query.options(DatastoreQueryOptions.parse(q));
		}

		if (t != null) {
			return JsonUtils.to(query.transform(t).list());
		}

		return JsonUtils.to(query.list());
	}

	private <T> String get(Repository r, IdRef<T> idRef, String t) {
		DatastoreQuery<T> query = executeQueryInRepository(r, idRef.getClazz()).whereById("=", idRef);
		return JsonUtils.to(t == null ? query.only() : query.transform(t).only());
	}

	private void saveInRepository(Repository r, Object object, IdRef<?> parentId) {
		// TODO remove dependence to EntityUtils, move EntityUtils inside
		// repository
		// TODO create tests to update a nested object, and check its idRef
		// inside a beforeSave hook
		EntityUtils.setParentId(object, parentId);
		if (enableHooks) {
			r.saveWithHooks(object);
		} else {
			r.save(object);
		}
	}

	private <T> DatastoreQuery<T> executeQueryInRepository(Repository r, Class<T> clazz) {
		if (enableHooks) {
			return r.queryWithHooks(clazz);
		} else {
			return r.query(clazz);
		}
	}
}
