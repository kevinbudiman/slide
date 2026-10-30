package trikita.jedux;


/**
 * Represents an action. They describe that something happened.
 * Actions are payloads of information that send data from your application to your store. T
 * They are the only source of information for the store. 
 * You send them to the store using store.dispatch().
 * 
 * @param <T> Enum which contains all possible actions for given store.
 * @param <V> Type for value which will be carried with given action.
 *
 * @example 
 * store.dispatch(new Action<>(CounterAction.INCREMENT))
 */
public class Action<T extends Enum, V> {

    /**
     * Type of action. Must be one possible value from enum T.
     */
    public final T type;
    
    /**
     * Value carried with action. That's how data from components to reducer can be transferred.
     */
    public final V value;

    /**
     * @param type type of action
     */
    public Action(T type) {
        this(type, null);
    }
   
    /**
     * @param type type of action
     * @param value value carried with given action
     */
    public Action(T type, V value) {
        this.type = type;
        this.value = value;
    }

    public String toString() {
        if (this.value != null) {
            return this.type.toString() + ": " + this.value.toString();
        } else {
            return this.type.toString();
        }
    }
}

