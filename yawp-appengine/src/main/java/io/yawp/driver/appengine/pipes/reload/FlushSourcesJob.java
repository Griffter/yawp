package io.yawp.driver.appengine.pipes.reload;

import com.google.appengine.tools.pipeline.Job2;
import com.google.appengine.tools.pipeline.Value;
import io.yawp.commons.utils.ReflectionUtils;
import io.yawp.driver.appengine.pipes.utils.WaiterJob;
import io.yawp.repository.IdRef;
import io.yawp.repository.Repository;
import io.yawp.repository.pipes.Pipe;
import io.yawp.repository.query.QueryBuilder;

import java.util.LinkedList;
import java.util.List;

import static io.yawp.repository.Yawp.yawp;

public class FlushSourcesJob extends Job2<Void, Class<? extends Pipe>, String> {

    private static final int BATCH_SIZE = 100;

    private transient Repository r;

    private transient Class<? extends Pipe> pipeClazz;

    private transient Class<?> sourceClazz;

    private transient String cursor;

    @Override
    public Value<Void> run(Class<? extends Pipe> pipeClazz, String cursor) throws Exception {
        init(pipeClazz, cursor);
        return execute();
    }

    private void init(Class<? extends Pipe> pipeClazz, String cursor) {
        this.r = yawp();
        this.pipeClazz = pipeClazz;
        this.sourceClazz = ReflectionUtils.getFeatureEndpointClazz(pipeClazz);
        this.cursor = cursor;
    }

    private Value<Void> execute() {
        List<Value<Void>> jobs = new LinkedList<>();

        List<? extends IdRef<?>> ids = sourceIds();

        if (cursor != null) {
            jobs.add(futureCall(new FlushSourcesJob(), immediate(pipeClazz), immediate(cursor)));
        }

        for (IdRef<?> id : ids) {
            jobs.add(futureCall(new FlushSourceJob(), immediate(pipeClazz), immediate(id.getUri())));
        }

        return futureCall(new WaiterJob(), futureList(jobs));
    }

    private List<? extends IdRef<?>> sourceIds() {
        QueryBuilder<?> q = r.query(sourceClazz).order("id").limit(BATCH_SIZE);
        if (cursor != null) {
            q.cursor(cursor);
        }
        List<? extends IdRef<?>> ids = q.ids();
        if (ids.size() < BATCH_SIZE) {
            cursor = null;
        } else {
            cursor = q.getCursor();
        }
        return ids;
    }
}
