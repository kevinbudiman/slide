package trikita.jedux;

import java.util.ArrayList;
import java.util.List;

/**
 * Store is smth which brings actions and reducers together. It holds state of the app.
 * It allow to get current state, subscribe to state changes, and to update state by dispatching actions.
 * It should be single point of truth in your application.
 * 
 * @param <A> Action type. Can be trikita.jedux.Action, or another implementation. State can't be mutated.
 * @param <S> State type. Objects of this type should be able to fully describe state of your app, and should be immutable.
 */
public final class Store<A, S> {

    /**
     * Reducers are objects which descibe how store state changes in response to actions.
     * @param <A> Action type. Can be trikita.jedux.Action, or another implementation
     * @param <S> State type. Objects of this type should be able to fully describe state of your app. State can't be mutated.
     */
    public interface Reducer<A, S> {
        /**
         * Reduce method should be able to calculate new state from current state.
         * It should be pure function - it shouldn't modify action, current state or any 3rd party objects.
         * Reducer called 100 times must return 100 times the same result, basing only on input parameters.
         * If reducer decide to not change state of store, then it should return unmodified currentState.
         * Otherwise it should return new state object or clone of currentState with applied modifications.
         * 
         * @param action action which describe what happened
         * @param currentState current state of store, which shouldn't be modified directly
         * @returns new state of store - can be also old state if nothing happened - it allows optimizations, and will 
         *          not trigger store subscribers.
         */
        S reduce(A action, S currentState);
    }

    /**
    * Middleware provides a third-party extension point between dispatching an action, 
    * and the moment it reaches the root reducer.
    * It's composable in a chain. 
    * You can use multiple independent third-party middleware in a single project.
    * 
    * @param <A> Action type. Can be trikita.jedux.Action, or another implementation
    * @param <S> State type used in your project
    */
    public interface Middleware<A, S> {
        
        /**
         * Method which enhance dispatch. It will receive action before it will reach root reducer.
         * Do your work, and call next.dispatch(action) to run another step in middleware chain.
         * Or firstly call dispatch, and then do your work, whatever you need.
         *
         * Example is @see Logger class.
         * @param store Store which is enhanced
         * @param action Action which is currently dispatching on enhanced store
         * @param next Call it like: next.dispatch(store) to run next piece in middleware chain. Otherwise store will break.
         */
        void dispatch(Store<A, S> store, A action, NextDispatcher<A> next);
    }

    /**
     * Objects with this interface allow dispatching actions. It can be store or middleware.
     * @param <A> action type used in your store.
     */
    public interface NextDispatcher<A> {
        /**
        * Call me to run next piece in middleware chain. otherwise it will break...
        * @param action Action which is currently dispatching on enhanced store
        */
        void dispatch(A action);
    }

    /** 
     * Current state of store
     */
    private S currentState;

    /**
     * Root reducer. 
     * Remember that it can be composed with more smaller reducers - to separate different activities
     */
    private final Reducer<A, S> reducer;
    
    /**
     * List of subscribers to state changes - everytime store will change state to new, runnables from this list will be called.
     */
    private final List<Runnable> subscribers = new ArrayList<>();

    private final Middleware<A, S> dispatcher = new Middleware<A, S>() {
        @Override
        public void dispatch(Store<A, S> store, A action, NextDispatcher<A> next) {
            synchronized (this) {
                currentState = store.reducer.reduce(action, currentState);
            }
            for (int i = 0; i < subscribers.size(); i++) {
                store.subscribers.get(i).run();
            }
        }
    };

    private final List<NextDispatcher<A>> next = new ArrayList<>();

    /**
     * Constructs new store
     * @param reducer Root reducer
     * @param state Initial state
     * @param middlewares Middlewares to register in store
     */
    public Store(Reducer<A, S> reducer, S state, Middleware<A, S> ...middlewares) {
        this.reducer = reducer;
        this.currentState = state;

        this.next.add(new NextDispatcher<A>() {
            public void dispatch(A action) {
                Store.this.dispatcher.dispatch(Store.this, action, null);
            }
        });
        for (int i = middlewares.length-1; i >= 0; i--) {
            final Middleware<A, S> mw = middlewares[i];
            final NextDispatcher<A> n = next.get(0);
            next.add(0, new NextDispatcher<A>() {
                public void dispatch(A action) {
                    mw.dispatch(Store.this, action, n);
                }
            });
        }
    }

    /**
     * Dispatch an action on given store.
     * @param action action to dispatch
     * @returns new state of store
     */
    public S dispatch(A action) {
        this.next.get(0).dispatch(action);
        return this.getState();
    }

    /**
     * @returns current state of store
     */
    public S getState() {
        return this.currentState;
    }

    /**
     * Add new subscriber listening for store's state changes.
     * @param r Runnable which will be called on store's state change.
     * @returns Runnable which will unsubscribe Runnable r from this store's state changes.
     */
    public Runnable subscribe(final Runnable r) {
        this.subscribers.add(r);
        return new Runnable() {
            public void run() {
                subscribers.remove(r);
            }
        };
    }
}
