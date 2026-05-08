package zh_examples;

public class OrderStateExample {
    public record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
        public OrderState withValid(boolean v) { return new OrderState(id, v, charged, shipped); }
        public OrderState withCharged(boolean c) { return new OrderState(id, valid, c, shipped); }
        public OrderState withShipped(boolean s) { return new OrderState(id, valid, charged, s); }
    }

    public static void main(String[] args) {
        OrderState s = new OrderState("o1", false, false, false);
        System.out.println("示例状态: " + s);
    }
}
