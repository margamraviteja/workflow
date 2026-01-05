function discount(price, isPremium) {
    return isPremium ? price * 0.8 : price;
}

// call the function using bound variables price and isPremium
discount(price, isPremium);
